package com.example.btchat.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException

private const val TAG = "ClientThread"

class ClientThread(
    private val socket: BluetoothSocket?,
    private val onConnected: (BluetoothSocket) -> Unit,
    private val onError: (Throwable) -> Unit
) : Thread() {
    override fun run() {
        try {
            socket?.connect()
            if (socket != null && socket.isConnected) onConnected(socket)
            else throw IOException("Socket null or not connected")
        } catch (e: IOException) {
            Log.e(TAG, "Connect failed", e)
            onError(e)
            try { socket?.close() } catch (_: IOException) {}
        }
    }
}
