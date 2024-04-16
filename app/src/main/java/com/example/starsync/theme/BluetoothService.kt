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
import java.io.IOException
import java.util.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.bluetooth.BluetoothManager


class BluetoothService(private val context : Context) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val microcontrollerUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val TAG = "Bluetooth Service"

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, onConnected: () -> Unit) {
        coroutineScope.launch {
            if (bluetoothAdapter == null) {
                // Bluetooth not supported on this device
                return@launch
            }
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            try {
                bluetoothSocket = device?.createRfcommSocketToServiceRecord(microcontrollerUUID)
                bluetoothSocket?.connect()
                // Connection successful
                Log.d(TAG, "connectToDevice() try block successful")
                onConnected()
            } catch (e: IOException) {
                // Connection failed
                Log.e(TAG, "Connection failed: ${e.message}\r\n${e.printStackTrace()}")
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
            try {
                val inputStream = bluetoothSocket?.inputStream ?: return@launch
                val buffer = ByteArray(1028)  // Consider testing different sizes

                while (isActive) {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val incomingMessage = String(buffer, 0, bytes)
                        Log.d(TAG, "Received data size: $bytes bytes")  // Log the size of the data received
                        withContext(Dispatchers.Main) {
                            onDataReceived(incomingMessage)
                        }
                        Log.d(TAG, "Received: $incomingMessage")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error receiving data: ${e.message}")
                }
            }
        }
    }

    private val completeData = StringBuilder()
    private var onDataComplete: ((String) -> Unit)? = null

    fun receiveMagnetometerData(onMagDataReceived: (String) -> Unit, onDataComplete: (String) -> Unit) {
        this.onDataComplete = onDataComplete
        coroutineScope.launch {
            if (bluetoothSocket == null) {
                Log.e(TAG, "receiveMagData() failed: BluetoothSocket is null")
                return@launch
            }
            try {
                val inputStream = bluetoothSocket?.inputStream ?: return@launch
                val buffer = ByteArray(1800) // Adjust for expected magnetometer data length (300 data-points, each 6 bytes)
                while (isActive) {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val dataString = String(buffer, 0, bytes)
                        if (dataString.contains("END_MESSAGE")){
                            completeData.append(dataString.substringBefore("END_DELIMITER"))
                            Log.d(TAG, "Found End Delimiter")
                            onDataComplete(completeData.toString())
                            completeData.clear()
                            return@launch
                        }
                        completeData.append(dataString)
                        onMagDataReceived(dataString)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error in receiving magnetometer data: ${e.message}")
            }
        }
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
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
