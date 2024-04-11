package com.example.starsync.theme

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.*


class BluetoothService(private val context : Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val microcontrollerUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val TAG = "Bluetooth Service"

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        coroutineScope.launch {
            if (bluetoothAdapter == null) {
                // Bluetooth not supported on this device
                return@launch
            }
            val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(deviceAddress)
            try {
                bluetoothSocket = device?.createRfcommSocketToServiceRecord(microcontrollerUUID)
                bluetoothSocket?.connect()
                // Connection successful
                // You could launch another coroutine here to listen for incoming data
                Log.d(TAG, "connectToDevice() try block successful")
            } catch (e: IOException) {
                // Connection failed
                Log.e(TAG, "Connection failed: ${e.message}")
                try {
                    bluetoothSocket?.close()
                } catch (ce: IOException) {
                    // Unable to close the socket
                    Log.e(TAG, "Close Socket Failed: ${e.message}")
                }
            }
        }
    }

    fun sendData(data: String) {
        coroutineScope.launch {
            if (bluetoothSocket == null) {
                Log.e(TAG, "sendData() failed: BluetoothSocket is null")
                return@launch
            }
            if (!bluetoothSocket!!.isConnected) {
                Log.e(TAG, "sendData() failed: BluetoothSocket is not connected")
                return@launch
            }
            try {
                val outputStream = bluetoothSocket?.outputStream
                if (outputStream == null) {
                    Log.e(TAG, "sendData() failed: OutputStream is null")
                    return@launch
                }
                // Log data being sent for verification
                Log.d(TAG, "sendData() success: Attempting to send data: $data")
                outputStream.write(data.toByteArray())
                outputStream.flush() // Ensure all data is sent
                // Data sent successfully
                Log.d(TAG, "sendData() success: Data has been flushed")
            } catch (e: IOException) {
                // Error sending data
                Log.e(TAG, "sendData() failed: IOException ${e.message}")
            } catch (e: Exception) {
                // Catch any other unexpected exceptions
                Log.e(TAG, "sendData() failed: Exception ${e.message}")
            }
        }
    }

    fun receiveData(onDataReceived: (String) -> Unit) {
        coroutineScope.launch {
            if (bluetoothSocket == null) {
                Log.e(TAG, "receiveData() failed: BluetoothSocket is null")
                return@launch
            }
            if (!bluetoothSocket!!.isConnected) {
                Log.e(TAG, "receiveData() failed: BluetoothSocket is not connected")
                return@launch
            }
            val listeningDurationMillis: Long = 10_000
            val result = withTimeoutOrNull(listeningDurationMillis) {
                try {
                    val inputStream = bluetoothSocket?.inputStream ?: return@withTimeoutOrNull
                    val buffer = ByteArray(1024) // Adjust buffer size as needed
                    while (isActive) { // Continuously listen for messages, but now with an escape hatch
                        val bytes = inputStream.read(buffer)
                        val incomingMessage = String(buffer, 0, bytes)
                        onDataReceived(incomingMessage) // Use the received data
                        Log.d(TAG, "receiveData() try block successful")
                    }
                } catch (e: IOException) {
                    // Error occurred when receiving data
                    Log.e(TAG, "receiveData() failed IOException: ${e.message}")
                    // This is a good place to handle reconnection or notify the user/UI thread
                }
                catch (e: Exception) {
                    // Catch any other unexpected exceptions
                    Log.e(TAG, "receiveData() failed: Exception ${e.message}")
                }
            }
            if (result == null) {
                Log.d(TAG, "Listening stopped due to timeout.")
            }
        }
    }

    fun disconnect() {
        coroutineScope.launch {
            try {
                bluetoothSocket?.close()
                bluetoothSocket = null
                // Successfully disconnected
            } catch (e: IOException) {
                Log.e(TAG, "Disconnect Failed: ${e.message}")
                // Error during disconnection
            }
        }
        coroutineScope.cancel()
    }

    fun cleanup() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing Bluetooth socket", e)
        }
        // If you have a coroutine job that listens for incoming data, cancel it here
        coroutineScope.cancel()
    }
}