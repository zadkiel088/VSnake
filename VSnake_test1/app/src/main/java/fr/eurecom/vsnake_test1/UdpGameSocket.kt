package fr.eurecom.vsnake_test1

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpGameSocket(
    private val isHost: Boolean,
    private val onMessageReceived: (String, InetAddress) -> Unit
) {
    companion object {
        private const val TAG = "UdpGameSocket"
        private const val PORT = 8888
        private const val HOST_IP = "192.168.49.1"
    }

    private val socket = DatagramSocket(PORT)
    private var running = true

    @Volatile
    private var connected = false

    fun isConnected(): Boolean = connected

    // Adresse du host (connue côté client)
    private val hostAddress: InetAddress =
        InetAddress.getByName(HOST_IP)

    // Adresse du client (apprise côté host)
    @Volatile
    var clientAddress: InetAddress? = null

    init {
        startListening()
    }

    // -------- RECEIVE --------

    private fun startListening() {
        Thread {
            val buffer = ByteArray(1024)

            while (running) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val msg = String(packet.data, 0, packet.length)

                    connected = true

                    // ⭐ Côté host : mémoriser l’adresse du client
                    if (isHost && clientAddress == null) {
                        clientAddress = packet.address
                        connected = true
                        Log.d("DEBUG_UDPGAMESOCKET_STARTLISTENING_1", "Client detected: $clientAddress")
                    }

                    onMessageReceived(msg, packet.address)

                } catch (e: Exception) {
                    if (running) {
                        Log.e("DEBUG_UDPGAMESOCKET_STARTLISTENING_2", "Receive error", e)
                    }
                }
            }
        }.start()
    }

    // -------- SEND --------

    fun send(message: String) {
        Thread {
            try {
                val target = if (isHost) {
                    clientAddress ?: return@Thread
                } else {
                    hostAddress
                }

                val data = message.toByteArray()
                val packet = DatagramPacket(data, data.size, target, PORT)
                socket.send(packet)

                Log.d("DEBUG_UDPGAMESOCKET_SEND_1", "TX → $message to $target")

            } catch (e: Exception) {
                Log.e("DEBUG_UDPGAMESOCKET_SEND_2", "Send error", e)
            }
        }.start()
    }

    fun close() {
        running = false
        socket.close()
    }
}
