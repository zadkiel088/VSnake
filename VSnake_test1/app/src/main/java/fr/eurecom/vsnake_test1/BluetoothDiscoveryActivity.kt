package fr.eurecom.vsnake_test1

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat.isLocationEnabled
import android.provider.Settings
import android.os.Handler
import android.os.Looper


class BluetoothDiscoveryActivity : AppCompatActivity() {

    private var debugOverlay: AccelerometerDebugOverlay? = null
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val devices = mutableListOf<String>()
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var scanInProgress = false
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    companion object {
        private const val REQ_BLUETOOTH = 100
        private const val SCAN_TIMEOUT = 20_000L // 10 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_discovery)

        listView = findViewById(R.id.listDevices)
        adapter = ArrayAdapter(this, R.layout.list_item_device, devices)
        listView.adapter = adapter

        findViewById<Button>(R.id.btnStartGame).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener {
            SensorHolder.controller?.calibrate()
        }

        findViewById<Button>(R.id.btnRescan).setOnClickListener {
            restartDiscovery()
        }

        debugOverlay = findViewById(R.id.debugOverlay)

        checkPermissionsAndStartScan()
    }

    override fun onResume() {
        super.onResume()
        debugOverlay?.register()
    }
    override fun onPause() {
        super.onPause()
        debugOverlay?.unregister()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}
        bluetoothAdapter?.cancelDiscovery()
        stopTimeout()
    }

    private fun checkPermissionsAndStartScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val missing = listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_BLUETOOTH)
                return
            }
        }

        if (!isLocationEnabled()) {
            askEnableLocation()
            return
        }

        startDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startDiscovery() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            bluetoothAdapter.enable()
        }

        scanInProgress = true

        devices.clear()
        devices.add("Searching...")
        adapter.notifyDataSetChanged()

        Toast.makeText(this, "Searching Devices...", Toast.LENGTH_SHORT).show()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) return

        bluetoothAdapter.startDiscovery()

        startTimeout()
    }

    private val receiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {

            when (intent.action) {

                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val entry = "${device?.name ?: "Unknown device"}\n${device?.address ?: "???"}"

                    devices.remove("Searching...")

                    if (!devices.contains(entry)) {
                        devices.add(entry)
                        adapter.notifyDataSetChanged()
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanInProgress = false
                    stopTimeout()

                    if (devices.isEmpty() || devices.contains("Searching...")) {
                        devices.clear()
                        devices.add("No devices found")
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun restartDiscovery() {
        bluetoothAdapter?.cancelDiscovery()
        stopTimeout()
        devices.clear()
        adapter.notifyDataSetChanged()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        startDiscovery()
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun askEnableLocation() {
        AlertDialog.Builder(this)
            .setTitle("Location required")
            .setMessage("Please enable Location to scan for Bluetooth devices.")
            .setPositiveButton("Open settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startTimeout() {
        stopTimeout()
        timeoutRunnable = Runnable {
            if (scanInProgress) {
                bluetoothAdapter?.cancelDiscovery()
                scanInProgress = false

                if (devices.size <= 1) { // only "Searching..."
                    devices.clear()
                    devices.add("No devices found")
                    adapter.notifyDataSetChanged()
                }

                Toast.makeText(this, "Scan timed out", Toast.LENGTH_SHORT).show()
            }
        }
        timeoutRunnable?.let { handler.postDelayed(it, SCAN_TIMEOUT) }
    }

    private fun stopTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

}