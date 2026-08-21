package com.example.myapplication // <-- Change this to your actual package name

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    // UI State variables
    private var isScanning = mutableStateOf(false)
    private var currentTemperature = mutableStateOf("--.-- C")
    private var deviceName = mutableStateOf("Disconnected")

    // BLE Scan Callback
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val scanRecord = result.scanRecord ?: return

            // Extract Manufacturer Specific Data for Company ID 0xFFFF (65535)
            val manufacturerData = scanRecord.getManufacturerSpecificData(0xFFFF)

            if (manufacturerData != null) {
                val rawPayload = String(manufacturerData, Charsets.UTF_8)

                // Check if payload starts with "TEMP:"
                if (rawPayload.startsWith("TEMP:")) {
                    val tempValue = rawPayload.removePrefix("TEMP:").trim()

                    // Update state variables to trigger Compose UI recomposition
                    currentTemperature.value = tempValue
                    deviceName.value = scanRecord.deviceName ?: result.device.name ?: "ESP32_TempBroadcast"

                    Log.d("BLE_TEMP", "Received Temp: $tempValue")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE_TEMP", "Scan failed with error code: $errorCode")
            isScanning.value = false
        }
    }

    // Runtime Permission Launcher for Android 12+ and Legacy Permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBleScan()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to scan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThermometerScreen(
                        temperature = currentTemperature.value,
                        device = deviceName.value,
                        isScanning = isScanning.value,
                        onToggleScan = {
                            if (isScanning.value) {
                                stopBleScan()
                            } else {
                                checkPermissionsAndScan()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startBleScan()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = bleScanner ?: run {
            Toast.makeText(this, "BLE Scanner not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Filter specifically for Company ID 0xFFFF
        val filter = ScanFilter.Builder()
            .setManufacturerData(0xFFFF, byteArrayOf())
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
        isScanning.value = true
        Log.d("BLE_TEMP", "Scan started...")
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        isScanning.value = false
        Log.d("BLE_TEMP", "Scan stopped.")
    }

    override fun onStop() {
        super.onStop()
        // Stop scanning when app goes to background to save battery
        if (isScanning.value) {
            stopBleScan()
        }
    }
}

@Composable
fun ThermometerScreen(
    temperature: String,
    device: String,
    isScanning: Boolean,
    onToggleScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ESP32 BLE Thermometer",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Device: $device",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = temperature,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onToggleScan,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(50.dp)
        ) {
            Text(
                text = if (isScanning) "Stop Scanning" else "Start Scanning",
                fontSize = 16.sp
            )
        }
    }
}