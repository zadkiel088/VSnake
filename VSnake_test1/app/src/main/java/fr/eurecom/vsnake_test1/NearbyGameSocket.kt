package fr.eurecom.vsnake_test1

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyGameSocket(
    private val context: Context,
    private val isHost: Boolean,
    private val onEndpointFoundCallback: (String, String) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private var onMessageReceived: (String) -> Unit
){
    companion object {
        private const val TAG = "NEARBY"
        private const val SERVICE_ID = "fr.eurecom.vsnake"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var endpointId: String? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                val msg = String(it)
                Log.d(TAG, "RXT: $msg")
                onMessageReceived(msg)
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {}
    }

    private val connectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {

            override fun onConnectionInitiated(
                endpointId: String,
                connectionInfo: ConnectionInfo
            ) {
                Log.d("DEBUG_NEARBYGAMESOCKET_ONCONNECTIONINITIATED", "Connection initiated with $endpointId")
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(
                endpointId: String,
                result: ConnectionResolution
            ) {
                if (result.status.isSuccess) {
                    Log.d("DEBUG_NEARBYGAMESOCKET_ONCONNECTIONRRESULT_1", "Connected to $endpointId")
                    this@NearbyGameSocket.endpointId = endpointId

                    if (isHost) {
                        send("Connection")
                        Log.d("DEBUG_NEARBYGAMESOCKET_ONCONNECTIONRRESULT_2", "demand received")
                    }

                    onConnected()
                } else {
                    Log.e("DEBUG_NEARBYGAMESOCKET_ONCONNECTIONRRESULT_3", "Connection failed")
                }
            }


            override fun onDisconnected(endpointId: String) {
                Log.d("DEBUG_NEARBYGAMESOCKET_ONDISCONNECTED", "Disconnected")
                this@NearbyGameSocket.endpointId = null
            }
        }

    fun startHosting() {
        Log.d("DEBUG_NEARBYGAMESOCKET_STARTHOSTING", "HOST: startAdvertising()")
        connectionsClient.startAdvertising(
            "Host",
            SERVICE_ID,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_POINT_TO_POINT)
                .build()
        )
    }

    fun startDiscovery(onFound: (String, String) -> Unit) {
        Log.d("DEBUG_NEARBYGAMESOCKET_STARTDISCOVERY_1", "CLIENT: startDiscovery()")
        connectionsClient.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(
                    endpointId: String,
                    info: DiscoveredEndpointInfo
                ) {
                    Log.d("DEBUG_NEARBYGAMESOCKET_STARTDISCOVERY_2", "FOUND endpoint=${info.endpointName}")
                    onEndpointFoundCallback(endpointId, info.endpointName)
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("DEBUG_NEARBYGAMESOCKET_STARTDISCOVERY_3", "LOST endpoint=$endpointId")
                }
            },
            DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_POINT_TO_POINT)
                .build()
        )
    }

    fun connect(endpointId: String) {
        connectionsClient.requestConnection(
            "Client",
            endpointId,
            connectionLifecycleCallback
        )
    }

    fun send(message: String) {
        endpointId?.let {
            Log.d("DEBUG_NEARBYGAMESOCKET_SEND", "TX: $message")
            connectionsClient.sendPayload(
                it,
                Payload.fromBytes(message.toByteArray())
            )
        }
    }

    fun stop() {
        Log.d("DEBUG_NEARBYGAMESOCKET_LOST", "LOST endpoint=$endpointId")
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
    }

    fun setOnMessageReceived(callback: (String) -> Unit) {
        onMessageReceived = callback
    }
}