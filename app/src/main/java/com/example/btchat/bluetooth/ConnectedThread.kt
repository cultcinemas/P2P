package com.example.btchat.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ConnectedThread"

class ConnectedThread(
    val socket: BluetoothSocket,
    private val onBytes: (ByteArray) -> Unit,
    private val onError: (Throwable) -> Unit
) : Thread() {

    private val inStream: InputStream = socket.inputStream
    private val outStream: OutputStream = socket.outputStream
    private val running = AtomicBoolean(true)

    override fun run() {
        val buffer = ByteArray(64 * 1024) // 64KB buffer
        while (running.get()) {
            try {
                val bytes = inStream.read(buffer) // blocks
                if (bytes == -1) break
                val data = buffer.copyOf(bytes)
                onBytes(data)
            } catch (e: IOException) {
                Log.e(TAG, "read failed", e)
                onError(e)
                break
            }
        }
        cancel()
    }

    fun write(bytes: ByteArray) {
        try {
            outStream.write(bytes)
            outStream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "write failed", e)
            onError(e)
        }
    }

    fun cancel() {
        running.set(false)
        try { inStream.close() } catch (_: IOException) {}
        try { outStream.close() } catch (_: IOException) {}
        try { socket.close() } catch (_: IOException) {}
    }
}
