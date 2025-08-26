package com.example.btchat.bluetooth

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.IOException
import java.util.*

private const val TAG = "BTManager"

class BluetoothManager(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP

    fun isSupported(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun getPairedDevices(): List<BluetoothDevice> {
        val set = adapter?.bondedDevices ?: emptySet()
        return set.toList()
    }

    fun startDiscovery(receiver: (BluetoothDevice) -> Unit) {
        if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
        adapter?.startDiscovery()
        // In Activity, register a BroadcastReceiver for ACTION_FOUND to call receiver(device)
    }

    fun createServerSocket(): BluetoothServerSocket? {
        val name = "BTChatServer"
        return try {
            adapter?.listenUsingRfcommWithServiceRecord(name, sppUuid)
        } catch (e: IOException) {
            Log.e(TAG, "Server socket create failed", e)
            null
        }
    }

    fun createClientSocket(device: BluetoothDevice): BluetoothSocket? {
        return try {
            device.createRfcommSocketToServiceRecord(sppUuid)
        } catch (e: IOException) {
            Log.e(TAG, "Client socket create failed", e)
            null
        }
    }
}
