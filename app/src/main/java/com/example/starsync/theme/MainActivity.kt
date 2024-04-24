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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import android.hardware.GeomagneticField
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.isActive


class MainActivity : ComponentActivity() {

    private lateinit var bluetoothService: BluetoothService

    private var isAppReady = mutableStateOf(false)
    private var magneticDataSent = false
    private var initalConnectMade = mutableStateOf(false)
    data class MagneticField(val x: Float, val y: Float, val z: Float)
    class SharedViewModel : ViewModel() {
        // List that holds the received messages
        val messages = mutableStateListOf<String>()
        val microcontrollerAddress: String = "98:D3:02:96:A2:05"

        //Some new stuff for receiving Magnetometer Data
        private val _calibratedData = MutableLiveData<String>()
        val calibratedData: LiveData<String> = _calibratedData

        fun updateCalibratedData(data: String) {
            _calibratedData.postValue(data)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        fun formatPointingData(value: Double): String {
            val integerPart = value.toInt().toString().padStart(3, '0')
            val decimalPart = ((value - value.toInt()) * 10000).toInt().toString().padStart(4, '0')
            return "$integerPart.$decimalPart"
        }
    }

    // Global variables to store location
    private var userLatitude = mutableStateOf<Double?>(null)
    private var userLongitude = mutableStateOf<Double?>(null)
    private var userAltitude = mutableStateOf<Double?>(null)
    private var xField = mutableStateOf<Float?>(null)
    private var yField = mutableStateOf<Float?>(null)
    private var zField = mutableStateOf<Float?>(null)
    private var dec = mutableStateOf<Float?>(null)



    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if both permissions are granted or not
            val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val bluetoothConnectPermissionGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestMultiplePermissionsLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT)
        )
        Log.d(TAG, "requestMultiplePermissionsLauncher called")

        val viewModel = SharedViewModel()
        getLocation()
        if (!::bluetoothService.isInitialized) {
            initializeBluetoothService(viewModel)
            initalConnectMade.value = true
        }
        Log.d(TAG, "getLocation() and initializeBluetoothService() called")

        //requestMultiplePermissionsLauncher.launch(
        //  arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT)
        //)
        //Log.d(TAG, "requestMultiplePermissionsLauncher called")

        // Set the content of the activity to the MainScreen composable
        setContent {
            StarSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAppReady.value) {
                        AppPager(userLatitude, userLongitude, userAltitude, xField, yField,
                            zField, dec, bluetoothService, viewModel, initalConnectMade)
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
        val viewModel = SharedViewModel()
        if (!::bluetoothService.isInitialized) {
            Log.d(TAG, "Reconnecting to device...")
            initializeBluetoothService(viewModel)
        }
    }


    private fun initializeBluetoothService(sharedViewModel: SharedViewModel) {
        // Initialize the bluetoothService before trying to access any of its methods
        bluetoothService = BluetoothService(this)

        // Now it's safe to check if it's connected
        if (bluetoothService.isConnected()) {
            Log.d(TAG, "Already connected. No need to reconnect.")
        }

        // Connect to the device, ensure permissions are granted before this step
        bluetoothService.connectToDevice(sharedViewModel.microcontrollerAddress) {
            // What we are doing on successful connection - sending magnetic field data.
            if (!magneticDataSent){
                Log.d(TAG, "Magnetic field data sending...")
                bluetoothService.sendData("M${xField.value},${yField.value},${zField.value},${dec.value}\n")
                magneticDataSent = true
            }
            Log.d(TAG, "Connection successful, start listening for data")
        }
        isAppReady.value = true
    }


    private fun getLocation() {
        Log.d("getLocation", "Entered getLocation()")
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            var location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location == null){
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            location?.let {
                userLatitude.value = it.latitude
                userLongitude.value = it.longitude
                userAltitude.value = it.altitude

                Log.d("getLocation", "$userLatitude $userLongitude $userAltitude")

                val geomagneticField = GeomagneticField(
                    it.latitude.toFloat(),
                    it.longitude.toFloat(),
                    it.altitude.toFloat(),
                    System.currentTimeMillis()
                )
                xField.value = geomagneticField.x
                yField.value = geomagneticField.y
                zField.value = geomagneticField.z
                dec.value = geomagneticField.declination
            }
        } else {
            // Prompt user to grant location permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            Log.d("getLocation result", "Else execution entered.")
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
fun MainScreen(latitudeState: MutableState<Double?>, longitudeState: MutableState<Double?>,
               altitudeState: MutableState<Double?>, xField: MutableState<Float?>,
               yField: MutableState<Float?>, zField: MutableState<Float?>,
               dec: MutableState<Float?>, bluetoothService: BluetoothService,
               viewModel: MainActivity.SharedViewModel
) {

    val TAG = "MainScreen"

    val listState = rememberLazyListState()

    val latitude = latitudeState.value
    val longitude = longitudeState.value
    val altitude = altitudeState.value
    val xfield = xField.value
    val yfield = yField.value
    val zfield = zField.value
    val declination = dec.value

    //Dropdown menu necessary variables
    val availableModes = listOf("Pointing", "Health", "Standby", "Magnetometer Calibration", "Accelerometer Calibration")
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
        Text(text = "X Magnetic Field: ${xfield ?: "Not Available"}")
        Text(text = "Y Magnetic Field: ${yfield ?: "Not Available"}")
        Text(text = "Z Magnetic Field: ${zfield ?: "Not Available"}")
        Text(text = "Magnetic Declination: ${declination ?: "Not Available"}")

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
                "Health" -> "H\n"
                "Standby" -> "S\n"
                "Magnetometer Calibration" -> "CM\n"
                "Accelerometer Calibration" -> "CA\n"
                else -> ""
            }
            if (modeCommand.isNotEmpty()) {
                if (modeCommand == "P") {
                    // Additional logic to handle the Pointing mode
                    val id = starIdInput.toIntOrNull()
                    val retrievedStarData =
                        getStarData(id!!, latitude!!, longitude!!)
                    // Serialize star data into a simple string format
                    val formattedAltitude = MainActivity.formatPointingData(retrievedStarData.altitude)
                    val formattedAzimuth = MainActivity.formatPointingData(retrievedStarData.azimuth)
                    val starDataString = "P$formattedAltitude,$formattedAzimuth\n"
                    // val starDataString = "P${String.format("%07.4f", retrievedStarData.altitude)}${String.format("%07.4f", retrievedStarData.azimuth)}"
                    // Send serialized data over Bluetooth
                    bluetoothService.sendData(starDataString)
                    Log.d(TAG, "called sendData() - P")
                }
                if (modeCommand == "H\n"){
                    bluetoothService.sendData(modeCommand)
                    Log.d(TAG, "called sendData() - H")
                }
                if (modeCommand == "S\n"){
                    bluetoothService.sendData(modeCommand)
                    Log.d(TAG, "called sendData() - S")
                }
                if (modeCommand == "CA\n"){
                    bluetoothService.sendData(modeCommand)
                    Log.d(TAG, "called sendData() - CA")
                }
                if (modeCommand == "CM\n"){
                    bluetoothService.sendData(modeCommand)
                    Log.d(TAG, "called sendData() - CM")
                    //Add logic to send dta that is actually calibrated
                }
            }
        }) {
            Text("Send Mode")
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


        MessageList(viewModel.messages, listState)
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
    // Create Python Instance
    val python = Python.getInstance()
    // Load Associated Script
    val pythonScript = python.getModule("fetchStarData")
    // Create PyObject
    val starDataPyObject: PyObject = pythonScript.callAttr("getStarData", starId, latitude, longitude)

    // Convert the PyObject to a Kotlin List
    val starDataList = starDataPyObject.asList()

    // Extracting values
    val altitudeValue = starDataList[0]?.toDouble() ?: 0.0
    val azimuthValue = starDataList[1]?.toDouble() ?: 0.0
    val visibleValue = starDataList[2]?.toBoolean() ?: false

    // Log message
    Log.d("StarData", "Altitude: $altitudeValue, Azimuth: $azimuthValue, Visible: $visibleValue")

    return StarData(altitudeValue, azimuthValue, visibleValue)
}

fun calibrateMag(stringData : String): String {

    // Check if the input string has at least 10 commas
    if (stringData.count { it == ',' } < 10) {
        Log.d("calibrateMag()", "Failed: Not enough data points")
        return "Failed"
    }

    Log.d("calibrateMag", stringData)

    // Create Python Instance
    val python = Python.getInstance()
    val pythonScript = python.getModule("magCalibration")["Magnetometer"]?.call() // Create an instance of the Magnetometer class
    return try{
        // Call magCalibration.calibrate() with stringData as parameter
        val result = pythonScript?.callAttr("calibrate", stringData).toString()
        Log.d("calibrateMag()", "Success! Iron Offsets:  $result")
        (result)
    } catch (e: Exception) {
        Log.e("calibrateMag()", "Error: $e")
        ("Failed")
    }
}

@Composable
fun MessageList(messages: List<String>, listState: LazyListState) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth() // Fill the maximum width available
            .heightIn(300.dp) // Set a maximum height for the LazyColumn
            .background(Color(0xFF121212))
    ) {
        items(messages) { message ->
            Text(
                text = message,
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .heightIn(max = 100.dp) // Limit the height of each message
            )
        }
    }
    ScrollToBottom(listState, messages)
}

@Composable
fun ScrollToBottom(listState: LazyListState, receivedMessages: List<String>) {
    LaunchedEffect(receivedMessages.size) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(index = listState.layoutInfo.totalItemsCount - 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPager(latitudeState: MutableState<Double?>, longitudeState: MutableState<Double?>,
             altitudeState: MutableState<Double?>, xField: MutableState<Float?>,
             yField: MutableState<Float?>, zField: MutableState<Float?>,
             dec: MutableState<Float?>, bluetoothService: BluetoothService,
             viewModel: MainActivity.SharedViewModel, initialConnect: MutableState<Boolean>) {

    val TAG = "AppPager"

    val pagerState = rememberPagerState()
    val reconnected = remember { mutableStateOf(false) }

    HorizontalPager(pageCount = 2, state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> MainScreen(
                latitudeState,
                longitudeState,
                altitudeState,
                xField,
                yField,
                zField,
                dec,
                bluetoothService, viewModel
            )

            1 -> CalibrationScreen(bluetoothService, viewModel)  // Assuming you create a separate composable for calibration
        }
    }
    LaunchedEffect(Unit) {
        while(this.isActive){
            delay(4000)
            if(!bluetoothService.isConnected()){
                reconnected.value = false
                Log.d(TAG, "Bluetooth connection lost! Attempting to reconnect from within AppPager...")
                bluetoothService.connectToDevice(viewModel.microcontrollerAddress) {
                }
            }else{
                if(!reconnected.value){
                    reconnected.value = true
                }
            }
        }
    }

    DisposableEffect(pagerState.currentPage, reconnected.value) {
        val onDataReceived: (String) -> Unit = { message ->
            viewModel.messages.add(message)
        }
        val onCalibrationDataReceived: (String) -> Unit = { calibrationData ->
            // Process calibration data
            viewModel.updateCalibratedData(calibrationData)
        }
        val job = CoroutineScope(Dispatchers.IO).launch {
            bluetoothService.receiveData(onDataReceived, onCalibrationDataReceived)
        }

        onDispose {
            // Clean up actions if needed when the composable leaves the composition
            job.cancel()
            Log.d(TAG, "AppPager coroutine closed.")
        }
    }
}

@Composable
fun CalibrationScreen(bluetoothService: BluetoothService, viewModel: MainActivity.SharedViewModel) {

    var calibratedData by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val TAG = "CalibrationScreen"

    var stringCalDat = "temp"

    val magneticFields = remember { mutableStateListOf<MainActivity.MagneticField>() }

    val listState = rememberLazyListState()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Calibration Page")

        // Additional UI elements for calibration
        val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

        viewModel.calibratedData.observe(lifecycleOwner, Observer { stringValue ->
            calibratedData = calibrateMag(stringValue)
            Log.d(TAG, "calibrateMag() called")
            stringCalDat = stringValue
        })

        LaunchedEffect(calibratedData) {
            calibratedData?.let {
                snackbarHostState.showSnackbar("Calibration updated: $it")
            }
        }

        Button(onClick = {
            if (calibratedData == null) {
                Log.d(TAG, "calibratedData is null in Send Calibrated Data Button")
            }
            calibratedData?.let {
                bluetoothService.sendData("CF")
                bluetoothService.sendData(it)
                Log.d(TAG, "sendData() called")
            }
        }) {
            Text("Send Calibrated Data")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Wont work with calibration data anyways.
        Text(text = "Received Bluetooth Data:")
        Spacer(modifier = Modifier.height(16.dp))
        //MessageList(viewModel.messages, listState)
        Text(text = stringCalDat)
    }
}