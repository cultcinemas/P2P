package com.example.btchat

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.btchat.bluetooth.*
import com.example.btchat.crypto.Crypto
import com.example.btchat.data.Message
import com.example.btchat.data.MessageType
import com.example.btchat.filetransfer.FileTransfer
import com.example.btchat.ui.EmojiPickerFragment
import com.example.btchat.ui.MessageAdapter
import com.example.btchat.util.Permissions
import com.example.btchat.util.Utils
import java.security.KeyPair
import javax.crypto.SecretKey

class MainActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MessageAdapter
    private lateinit var bt: BluetoothManager

    private var serverThread: ServerThread? = null
    private val connections = mutableListOf<ConnectedThread>()

    private lateinit var rsa: KeyPair
    private val peersSessionKeys = mutableMapOf<String, SecretKey>() // deviceAddress -> AES

    private val handler = Handler(Looper.getMainLooper())

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sendFileToAll(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Permissions.hasAll(this)) Permissions.requestAll(this)

        bt = BluetoothManager(this)
        rsa = Crypto.generateRSAKeyPair()

        // UI
        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvMessages)
        adapter = MessageAdapter()
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rv.adapter = adapter

        val btnScan: Button = findViewById(R.id.btnScan)
        val btnHost: Button = findViewById(R.id.btnHost)
        val btnSend: Button = findViewById(R.id.btnSend)
        val btnEmoji: Button = findViewById(R.id.btnEmoji)
        val btnChooseFile: Button = findViewById(R.id.btnChooseFile)
        val etMessage: com.google.android.material.textfield.TextInputEditText = findViewById(R.id.etMessage)

        viewModel.messages.observe(this) { adapter.submitList(it); rv.scrollToPosition(it.size - 1) }

        btnHost.setOnClickListener { startHosting() }
        btnScan.setOnClickListener { showDevicesDialog() }
        btnChooseFile.setOnClickListener { filePicker.launch("*") }

        btnEmoji.setOnClickListener {
            EmojiPickerFragment { emoji ->
                etMessage.text?.insert(etMessage.selectionStart.coerceAtLeast(0), emoji)
            }.show(supportFragmentManager, "emoji")
        }

        btnSend.setOnClickListener {
            val text = etMessage.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                sendTextToAll(text)
                etMessage.setText("")
            }
        }

        // Register for discovery results
        registerReceiver(foundReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(stateReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(foundReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        serverThread?.cancel()
        connections.forEach { it.cancel() }
    }

    private fun startHosting() {
        val server = bt.createServerSocket() ?: run {
            toast("Server socket failed")
            return
        }
        serverThread = ServerThread(server, onClientConnected = { sock ->
            onSocketConnected(sock)
        }, onError = { e -> toast("Server error: ${e.message}") })
        serverThread?.start()
        toast("Hosting… waiting for peers")
    }

    private fun showDevicesDialog() {
        val paired = bt.getPairedDevices()
        val names = paired.map { it.name + "\n" + it.address }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.devices))
            .setItems(names.toTypedArray()) { d, idx ->
                connectTo(paired[idx])
            }
            .create()
        dialog.show()
        // Also start discovery to find new devices
        BluetoothAdapter.getDefaultAdapter()?.startDiscovery()
    }

    private fun connectTo(device: BluetoothDevice) {
        val socket = bt.createClientSocket(device)
        val client = ClientThread(socket, onConnected = { sock ->
            onSocketConnected(sock)
        }, onError = { e -> toast("Connect failed: ${e.message}") })
        client.start()
    }

    private fun onSocketConnected(sock: BluetoothSocket) {
        runOnUiThread { toast("Connected: ${sock.remoteDevice.name}") }
        val ct = ConnectedThread(sock, onBytes = { bytes ->
            handleIncoming(bytes, sock)
        }, onError = { e -> toast("Conn error: ${e.message}") })
        synchronized(connections) { connections.add(ct) }
        ct.start()

        // After connection, exchange RSA public keys and negotiate AES
        performHandshake(sock)
    }

    private fun performHandshake(sock: BluetoothSocket) {
        // 1) Send my RSA public key
        val pub = rsa.public.encoded
        sendRaw(sock, buildPacket("PUB", pub))
    }

    private fun handleIncoming(bytes: ByteArray, sock: BluetoothSocket) {
        // Packets: 3-byte type + 4-byte length + payload
        if (bytes.size < 7) return
        val type = String(bytes.copyOfRange(0, 3))
        val len = java.nio.ByteBuffer.wrap(bytes.copyOfRange(3, 7)).int
        val payload = bytes.copyOfRange(7, 7 + len)

        when (type) {
            "PUB" -> { // received peer RSA public key, send session key
                val k = Crypto.generateAESKey()
                peersSessionKeys[sock.remoteDevice.address] = k
                val encK = Crypto.encryptRSA(java.security.KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(payload)), k.encoded)
                sendRaw(sock, buildPacket("KEY", encK))
            }
            "KEY" -> { // received encrypted AES key
                val aesBytes = Crypto.decryptRSA(rsa.private, payload)
                val key = Crypto.keyFromBytes(aesBytes)
                peersSessionKeys[sock.remoteDevice.address] = key
                runOnUiThread { toast("Secure channel ready with ${sock.remoteDevice.name}") }
            }
            "TXT" -> {
                val key = peersSessionKeys[sock.remoteDevice.address] ?: return
                val plain = Crypto.decryptAES(key, payload)
                val text = String(plain)
                val msg = Message(
                    id = Utils.id(),
                    fromDevice = sock.remoteDevice.address,
                    toDevice = null,
                    type = MessageType.TEXT,
                    text = text,
                    isSelf = false
                )
                runOnUiThread { viewModel.addMessage(msg) }
                // Broadcast to others (group)
                broadcastExcept(sock, buildPacket("TXT", payload))
            }
            "FIL" -> {
                val key = peersSessionKeys[sock.remoteDevice.address] ?: return
                val plain = Crypto.decryptAES(key, payload)
                // First bytes: filenameLen(2) + filename + fileContent
                val bb = java.nio.ByteBuffer.wrap(plain)
                val nameLen = bb.short.toInt()
                val nameBytes = ByteArray(nameLen)
                bb.get(nameBytes)
                val fileName = String(nameBytes)
                val content = ByteArray(plain.size - 2 - nameLen)
                bb.get(content)
                // Save to cache
                val file = java.io.File(cacheDir, fileName)
                file.outputStream().use { it.write(content) }
                val msg = Message(
                    id = Utils.id(),
                    fromDevice = sock.remoteDevice.address,
                    toDevice = null,
                    type = MessageType.FILE,
                    text = "Received file: $fileName",
                    fileName = fileName,
                    fileSize = content.size.toLong(),
                    isSelf = false
                )
                runOnUiThread { viewModel.addMessage(msg) }
                broadcastExcept(sock, buildPacket("FIL", payload))
            }
        }
    }

    private fun buildPacket(type: String, payload: ByteArray): ByteArray {
        val header = type.toByteArray()
        val len = java.nio.ByteBuffer.allocate(4).putInt(payload.size).array()
        return header + len + payload
    }

    private fun sendRaw(sock: BluetoothSocket, bytes: ByteArray) {
        synchronized(connections) {
            connections.find { it.socket == sock }?.write(bytes)
        }
    }

    private fun broadcast(bytes: ByteArray) {
        synchronized(connections) { connections.forEach { it.write(bytes) } }
    }

    private fun broadcastExcept(except: BluetoothSocket, bytes: ByteArray) {
        synchronized(connections) { connections.filter { it.socket != except }.forEach { it.write(bytes) } }
    }

    private fun sendTextToAll(text: String) {
        // Encrypt per-peer with their AES key
        synchronized(connections) {
            connections.forEach { ct ->
                val key = peersSessionKeys[ct.socket.remoteDevice.address] ?: return@forEach
                val enc = Crypto.encryptAES(key, text.toByteArray())
                ct.write(buildPacket("TXT", enc))
            }
        }
        val msg = Message(Utils.id(), "me", null, MessageType.TEXT, text, null, null, System.currentTimeMillis(), true, true)
        viewModel.addMessage(msg)
    }

    private fun sendFileToAll(uri: Uri) {
        val name = queryName(uri)
        synchronized(connections) {
            connections.forEach { ct ->
                val key = peersSessionKeys[ct.socket.remoteDevice.address] ?: return@forEach
                // Stream file in memory (for simplicity). For very large files, chunk & multiple packets.
                val baos = java.io.ByteArrayOutputStream()
                contentResolver.openInputStream(uri)?.use { it.copyTo(baos) }
                val content = baos.toByteArray()
                val nameBytes = name.toByteArray()
                val bb = java.nio.ByteBuffer.allocate(2 + nameBytes.size + content.size)
                bb.putShort(nameBytes.size.toShort())
                bb.put(nameBytes)
                bb.put(content)
                val enc = Crypto.encryptAES(key, bb.array())
                ct.write(buildPacket("FIL", enc))
            }
        }
        val msg = Message(Utils.id(), "me", null, MessageType.FILE, "Sent file: $name", name, null, System.currentTimeMillis(), true, true)
        viewModel.addMessage(msg)
    }

    private fun queryName(uri: Uri): String {
        var name = "file"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
        }
        return name
    }

    // Bluetooth discovery receivers
    private val foundReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    // Auto-attempt connection to newly found paired device? Prefer manual select.
                }
            }
        }
    }

    private val stateReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Discovery finished
        }
    }

    private fun toast(s: String) = handler.post { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
}
