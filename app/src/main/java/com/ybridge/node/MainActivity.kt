package com.ybridge.node

import android.os.Bundle
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import java.net.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        logView = TextView(this).apply { 
            textSize = 12f
            setPadding(30, 30, 30, 30)
            text = "YBRIDGE NODE DEBUG MODE\n" + "=".repeat(30) + "\n"
        }
        scroll.addView(logView)
        setContentView(scroll)

        thread { 
            val addresses = mutableListOf<InetAddress>()
            try {
                NetworkInterface.getNetworkInterfaces().asSequence().forEach { ni ->
                    val name = ni.name
                    runOnUiThread { logView.append("\nInterface: $name\n") }
                    
                    ni.inetAddresses.asSequence().forEach { addr ->
                        val ip = addr.hostAddress
                        runOnUiThread { logView.append(" - Found: $ip\n") }
                        
                        // Kriteria IP Global (Bukan lokal/loopback)
                        if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress && addr is Inet6Address) {
                            addresses.add(addr)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { logView.append("\nError: ${e.message}\n") }
            }

            val ports = listOf(2000, 2001, 2002, 2003, 2004)
            runOnUiThread { logView.append("\n" + "=".repeat(30) + "\nFinal Selection:\n") }
            
            ports.forEachIndexed { index, port ->
                val bindIp = if (addresses.isNotEmpty()) addresses[index % addresses.size] else null
                thread { startProxy(port, bindIp) }
                runOnUiThread { 
                    logView.append("Slot ${index+1} (Port $port) -> ${bindIp?.hostAddress ?: "IPv4 Default"}\n") 
                }
            }
        }
    }

    private fun startProxy(port: Int, bindIp: InetAddress?) {
        try {
            val server = ServerSocket(port)
            while (true) {
                val client = server.accept()
                thread {
                    try {
                        client.soTimeout = 10000
                        val input = client.getInputStream()
                        val output = client.getOutputStream()

                        // Negotiation
                        input.read(); val n = input.read(); input.readNBytes(n)
                        output.write(byteArrayOf(0x05, 0x00))

                        // Request
                        input.read(); input.read(); input.read()
                        val atype = input.read()
                        val address = when (atype) {
                            0x01 -> InetAddress.getByAddress(input.readNBytes(4)).hostAddress
                            0x03 -> String(input.readNBytes(input.read()))
                            0x04 -> InetAddress.getByAddress(input.readNBytes(16)).hostAddress
                            else -> return@thread
                        }
                        val dPort = ((input.read() and 0xff) shl 8) or (input.read() and 0xff)

                        val remote = Socket()
                        if (bindIp != null) try { remote.bind(InetSocketAddress(bindIp, 0)) } catch(e: Exception) {}
                        remote.connect(InetSocketAddress(address, dPort), 10000)
                        
                        output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                        thread { remote.getInputStream().copyTo(output) }
                        input.copyTo(remote.getOutputStream())
                    } catch (e: Exception) {} finally { client.close() }
                }
            }
        } catch (e: Exception) {}
    }
}
