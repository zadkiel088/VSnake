package fr.eurecom.vsnake_test1

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WifiDiscoveryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val items = ArrayList<String>()
    private val endpoints = HashMap<String, String>()

    private lateinit var btnHost: Button
    private lateinit var btnConnect: Button
    private lateinit var btnPlay: Button
    private lateinit var btnCancel: Button

    private var isHost = false
    private val REQ_NEARBY = 42
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_discovery)

        listView = findViewById(R.id.listDevices)
        btnHost = findViewById(R.id.btnHost)
        btnConnect = findViewById(R.id.btnConnect)
        btnPlay = findViewById(R.id.btnStartGame)
        btnCancel = findViewById(R.id.btnCancel)

        adapter = ArrayAdapter(this, R.layout.list_item_wifi, items)
        listView.adapter = adapter

        btnPlay.isEnabled = false

        btnHost.setOnClickListener {
            isHost = true

            NetworkManager.socket = NearbyGameSocket(
                context = this,
                isHost = true,
                onEndpointFoundCallback = { _, _ -> },
                onConnected = {
                    runOnUiThread {
                        Toast.makeText(this, "Client connected", Toast.LENGTH_SHORT).show()
                        btnPlay.isEnabled = true
                    }
                },
                onDisconnected = {
                    Log.d("DEBUG_WIFIMENU_ONDISCONNECTED", "Disconnected")
                },
                onMessageReceived = { msg ->
                    Log.d("DEBUG_WIFIMENU_ONMESSAGERECEIVED", "Response=$msg")
                    if (msg == "START") {
                        runOnUiThread {
                            Log.d("DEBUG_WIFIMENU_ONCMESSAGERECEIVED_START", "going to launch the game")
                            launchGame(isHost = false)
                        }
                    }
                }
            )

            NetworkManager.socket.startHosting()

            items.clear()
            items.add("Waiting for player…")
            adapter.notifyDataSetChanged()
        }


        btnConnect.setOnClickListener {
            isHost = false

            NetworkManager.socket = NearbyGameSocket(
                context = this,
                isHost = false,
                onEndpointFoundCallback = { endpointId, name ->
                    runOnUiThread {
                        endpoints[name] = endpointId
                        items.add(name)
                        adapter.notifyDataSetChanged()
                    }
                },
                onConnected = {
                    Log.d("DEBUG_WIFIMENU_MANAGER_ONCONNECTED", "Connected to host")
                },
                onDisconnected = {
                    Log.d("DEBUG_WIFIMENU_MANAGER_ONDISCONNECTED", "Disconnected")
                },
                onMessageReceived = { }
            )

            if (!hasNearbyPermissions()) {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQ_NEARBY)
                return@setOnClickListener
            }

            NetworkManager.socket.startDiscovery { _, _ -> }
        }



        listView.setOnItemClickListener { _, _, pos, _ ->
            val name = items[pos]
            endpoints[name]?.let {
                NetworkManager.socket.connect(it)

                NetworkManager.socket.setOnMessageReceived { msg ->
                    Log.d("DEBUG_WIFIMENU_LISTENER", "Response=$msg")

                    if (msg == "START") {
                        runOnUiThread {
                            launchGame(isHost = false)
                        }
                    }
                }

                Toast.makeText(this, "Connecting to host…", Toast.LENGTH_SHORT).show()
            }
        }

        btnPlay.setOnClickListener {
            NetworkManager.socket.send("START")
            launchGame(true)
        }

        btnCancel.setOnClickListener {
            Log.d("DEBUG_WIFIMENU_CANCELBTN", "CANCEL pressed → stopping discovery/hosting")
            NetworkManager.socket.stop()
            items.clear()
            adapter.notifyDataSetChanged()
            btnPlay.isEnabled = false
        }
    }

    private fun launchGame(isHost: Boolean) {

        val selectedCityName = intent.getStringExtra("selectedCity")

        val intentGame = Intent(this, MainActivity::class.java)
        intentGame.putExtra("IS_HOST", isHost)
        intentGame.putExtra("selectedCity", selectedCityName)

        startActivity(intentGame)
        finish()
    }


    private fun hasNearbyPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startNearbyDiscovery() {
        NetworkManager.socket.startDiscovery { id, name ->
            runOnUiThread {
                if (!endpoints.containsKey(name)) {
                    endpoints[name] = id
                    items.add(name)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_NEARBY &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            startNearbyDiscovery()
        } else {
            Toast.makeText(
                this,
                "All permissions are required for multiplayer",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}