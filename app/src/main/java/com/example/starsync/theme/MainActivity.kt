package com.example.starsync.theme

// Import necessary libraries and modules
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.example.starsync.theme.ui.StarSyncTheme


class MainActivity : ComponentActivity() {

    private lateinit var bluetoothService: BluetoothService

    private var isAppReady = mutableStateOf(false)

    companion object {
        private const val TAG = "MainActivity"
    }

    // Global variables to store location
    private var userLatitude = mutableStateOf<Double?>(null)
    private var userLongitude = mutableStateOf<Double?>(null)
    private var userAltitude = mutableStateOf<Double?>(null)


    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if both permissions are granted or not
            val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val bluetoothConnectPermissionGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false

            if (locationPermissionGranted && bluetoothConnectPermissionGranted) {
                // Location and bluetooth permission is granted
                getLocation()
                initializeBluetoothService()
                Log.d(TAG, "getLocation() and initializeBluetoothService() called")
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied.
                Toast.makeText(this, "Location and Bluetooth permissions are required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestMultiplePermissionsLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT)
        )
        Log.d(TAG, "requestMultiplePermissionsLauncher called")

        // Set the content of the activity to the MainScreen composable
        setContent {
            StarSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAppReady.value) {
                        MainScreen(userLatitude, userLongitude, userAltitude, bluetoothService)
                        Log.d(TAG, "MainScreen() called")
                    } else {
                        // Show a loading or placeholder screen
                        CircularProgressIndicator() // Or another appropriate widget
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if the Bluetooth connection is active and reconnect if not
        if (!bluetoothService.isConnected()) {
            Log.d(TAG, "Reconnecting to device...")
            initializeBluetoothService()
        }
    }

    private fun initializeBluetoothService() {
        if (bluetoothService.isConnected()) {
            Log.d(TAG, "Already connected. No need to reconnect.")
            return
        }
        bluetoothService = BluetoothService(this)
        // Connect to the device, consider moving this to after permissions are granted
        val microcontrollerAddress: String = "98:D3:02:96:A2:05"
        bluetoothService.connectToDevice(microcontrollerAddress)
        isAppReady.value = true
    }

    private fun getLocation() {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            location?.let {
                userLatitude.value = it.latitude
                userLongitude.value = it.longitude
                userAltitude.value = it.altitude
                // Use the location coordinates as necessary
            } ?: run {
                Toast.makeText(this, "Location not available", Toast.LENGTH_LONG).show()
            }
        }
    }

    //Cancels coroutines (separate threads) when we terminate app
    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.disconnect()
        bluetoothService.cleanup()
    }

}

// Composable function to display a greeting message
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

// Preview function to see how the Greeting composable looks in the design tool
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StarSyncTheme {
        Greeting("Stargazer")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarInput(onValueChange: (String) -> Unit) {
    var starId by remember { mutableStateOf("") }
    // State to hold the error message
    var errorMessage by remember { mutableStateOf<String?>(null) }

    TextField(
        value = starId,
        onValueChange = { newValue ->
            val isValidNumber = newValue.toIntOrNull()?.let { num -> num in 1..120404 } ?: false
            if (isValidNumber || newValue.isEmpty()) {
                starId = newValue
                onValueChange(newValue)
                errorMessage = null
            } else {
                errorMessage = "Invalid Star ID"
            }
        },
        label = { Text("Enter Star ID") },

        singleLine = true,

        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        )
    )

    // Display the error message below the TextField if it's not null
    errorMessage?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
    }
}

// MainScreen composable that wraps the main content of the app
@Composable
fun MainScreen(latitudeState: MutableState<Double?>, longitudeState: MutableState<Double?>, altitudeState: MutableState<Double?>, bluetoothService: BluetoothService) {

    val TAG = "MainScreen"

    // Define a state to hold the received message
    var receivedMessage by remember { mutableStateOf<String?>(null) }

    val latitude = latitudeState.value
    val longitude = longitudeState.value
    val altitude = altitudeState.value

    //Dropdown menu necessary variables
    val availableModes = listOf("Pointing", "Health", "Standby", "Calibration")
    val selectedMode = remember { mutableStateOf(availableModes[0]) }

    val context = LocalContext.current
    // State to hold the star ID input from the user
    var starIdInput by remember { mutableStateOf("") }

    // State to hold the retrieved star data
    var starData by remember { mutableStateOf<StarData?>(null) }

    // State to hold a message indicating the status of the star data retrieval process.
    var fetchStatusMessage by remember { mutableStateOf("") }

    Spacer(modifier = Modifier.height(8.dp))

    // Use a Column to arrange composable vertically
    Column(modifier = Modifier.padding(16.dp)) {

        // Display User Location
        Text(text = "Latitude: ${latitude ?: "Not Available"}")
        Text(text = "Longitude: ${longitude ?: "Not Available"}")
        Text(text = "Altitude: ${altitude ?: "Not Available"}")

        Spacer(modifier = Modifier.height(16.dp))

        // Use the StarInput composable to get input from the user
        StarInput(onValueChange = { input ->
            starIdInput = input
        })

        // Button to fetch the star data
        Button(onClick = {
            val id = starIdInput.toIntOrNull()

            val isStarIdValid = id != null
            val isLocationAvailable = latitude != null && longitude != null

            // Diagnostic toasts
            Toast.makeText(context, "Star ID not null: $isStarIdValid, Location available: $isLocationAvailable", Toast.LENGTH_LONG).show()

            if (isStarIdValid && isLocationAvailable) {
                try {
                    val retrievedStarData =
                        getStarData(id!!, latitude!!, longitude!!)
                    starData = retrievedStarData
                    fetchStatusMessage = "Star data retrieved successfully."

                } catch (e: Exception) {
                    // Log the exception
                    fetchStatusMessage = "Exception in retrieving star data: ${e.message}"
                }
            } else {
                // This message will now be more specific based on the checks
                fetchStatusMessage = when {
                    !isStarIdValid -> "Invalid Star ID."
                    !isLocationAvailable -> "Location not available."
                    else -> "Invalid Star ID or Location not available."
                }
            }
        }) {
            Text("Fetch Star Data")
        }

        Spacer(modifier = Modifier.height(8.dp))
        ModeSelectionDropdown(selectedMode, availableModes)

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val modeCommand = when (selectedMode.value) {
                "Pointing" -> "P"
                "Health" -> "H"
                "Standby" -> "S"
                "Calibration" -> "C"
                else -> ""
            }
            if (modeCommand.isNotEmpty()) {
                if (modeCommand == "P") {
                    // Additional logic to handle the Pointing mode
                    val id = starIdInput.toIntOrNull()
                    val retrievedStarData =
                        getStarData(id!!, latitude!!, longitude!!)
                    // Serialize star data into a simple string format
                    val starDataString = "P$${String.format("%07.4f", retrievedStarData.altitude)}${String.format("%07.4f", retrievedStarData.azimuth)}"
                    // Send serialized data over Bluetooth
                    bluetoothService.sendData(starDataString)
                    Log.d(TAG, "called sendData() - P")
                }
                if (modeCommand == "H"){
                    bluetoothService.sendData(modeCommand)
                    Log.d(TAG, "called sendData() - H")
                    bluetoothService.receiveData { message ->
                        receivedMessage = message }
                    Log.d(TAG, "called ReceiveData() - H")
                }
                if (modeCommand == "S"){
                    bluetoothService.sendData(modeCommand)
                    Log.d(TAG, "called sendData() - S")
                }
                if (modeCommand == "C"){
                    bluetoothService.sendData(modeCommand)
                    Log.d(TAG, "called sendData() - C")
                }
            }
        }) {
            Text("Send Mode")
        }

        // Display the received message if not null
        receivedMessage?.let {
            Text("Received: $it")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Display the status message below the button
        Text(text = fetchStatusMessage)
        Spacer(modifier = Modifier.height(4.dp))

        //Display all relevant star data
        starData?.let {
            Text("Altitude: ${it.altitude}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Azimuth: ${it.azimuth}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Visibility: ${if (it.visible) "Visible" else "Not Visible"}")
        }

        Spacer(modifier = Modifier.height(4.dp)) // Spacer before entered ID

        Text(text = "Entered Star ID: $starIdInput")

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Received Bluetooth Data:")
        Spacer(modifier = Modifier.height(4.dp))
        receivedMessage?.let {
            Text(text = "Received Data: $it")
        }
    }
}

//call drop down menu in MainScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectionDropdown(selectedMode: MutableState<String>, availableModes: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
        }
    ) {
        TextField(
            modifier = Modifier.menuAnchor(),
            readOnly = true,
            value = selectedMode.value,
            onValueChange = { },
            label = { Text("Select Mode") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            //modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableModes.forEachIndexed() { index, mode ->
                DropdownMenuItem(
                    text = { Text(text = mode) },
                    onClick = {
                        selectedMode.value = availableModes[index]
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

fun getStarData(starId: Int, latitude: Double, longitude: Double): StarData {
    val python = Python.getInstance()
    val pythonScript = python.getModule("fetchStarData")
    val starDataPyObject: PyObject = pythonScript.callAttr("getStarData", starId, latitude, longitude)

    // Convert the PyObject to a Kotlin List<Any?>
    val starDataList = starDataPyObject.asList()

    // Extracting values for toast message
    val altitudeValue = starDataList[0]?.toDouble() ?: 0.0
    val azimuthValue = starDataList[1]?.toDouble() ?: 0.0
    val visibleValue = starDataList[2]?.toBoolean() ?: false

    // Log message
    Log.d("StarData", "Altitude: $altitudeValue, Azimuth: $azimuthValue, Visible: $visibleValue")

    return StarData(altitudeValue, azimuthValue, visibleValue)
}
