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
import kotlinx.coroutines.withTimeoutOrNull


class BluetoothService(private val context : Context) {
    private var isCalibrationMode = false
    private val calibrationDataBuffer = StringBuilder()

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
                // Initialize socket and connect
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

    fun receiveData(onDataReceived: (String) -> Unit, onCalibrationDataReceived: (String) -> Unit) {
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
                        if (incomingMessage.contains("CM_BEGIN")) {
                            Log.d(TAG, "receiveData() found magnetic calibration data.")
                            isCalibrationMode = true
                            calibrationDataBuffer.clear()

                            // Start a timeout coroutine that will end the calibration mode if no "CM_END" is received within 30 seconds
                            withTimeoutOrNull(30000L) {  // 30000 milliseconds = 30 seconds
                                while (isCalibrationMode && isActive) {
                                    val newBytes = inputStream.read(buffer)
                                    val newMessage = String(buffer, 0, newBytes)
                                    if (newMessage.contains("CM_END")) { // can change this to any message delimiter we want to
                                        onCalibrationDataReceived(calibrationDataBuffer.toString())
                                        isCalibrationMode = false
                                    } else {
                                        calibrationDataBuffer.append(newMessage)
                                    }
                                }
                            }
                            if (isCalibrationMode) {
                                // Timeout occurred without receiving "END_MESSAGE"
                                isCalibrationMode = false
                                Log.d(TAG, "Calibration timeout: No ENDCM received")
                                // You can also decide to call onCalibrationDataReceived with what has been collected so far, or handle the timeout case differently
                            }
                        } else if (!isCalibrationMode) {
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
