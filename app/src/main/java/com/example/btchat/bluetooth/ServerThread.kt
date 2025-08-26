package com.example.btchat.bluetooth

import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "ServerThread"

class ServerThread(
    private val serverSocket: BluetoothServerSocket?,
    private val onClientConnected: (BluetoothSocket) -> Unit,
    private val onError: (Throwable) -> Unit
) : Thread() {

    @Volatile private var running = true

    override fun run() {
        try {
            while (running) {
                val socket = serverSocket?.accept() // blocks
                if (socket != null) {
                    onClientConnected(socket)
                } else {
                    break
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Accept failed", e)
            onError(e)
        } finally {
            try { serverSocket?.close() } catch (_: IOException) {}
        }
    }

    fun cancel() {
        running = false
        try { serverSocket?.close() } catch (_: IOException) {}
    }
}
