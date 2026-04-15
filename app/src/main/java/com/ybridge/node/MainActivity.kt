package com.ybridge.node

import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import java.net.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply { text = "Ybridge Node Starting...\n" }
        setContentView(LinearLayout(this).apply { addView(logView) })

        val ports = listOf(2000, 2001, 2002, 2003, 2004)
        val ipv6Addresses = getAvailableIpv6()

        ports.forEachIndexed { index, port ->
            val bindIp = if (ipv6Addresses.size > index) ipv6Addresses[index] else null
            thread { startProxy(port, bindIp) }
            logView.append("[*] Slot $index: Port $port -> ${bindIp ?: "Default IPv4"}\n")
        }
    }

    private fun getAvailableIpv6(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            NetworkInterface.getNetworkInterfaces().asSequence().forEach { ni ->
                ni.inetAddresses.asSequence().forEach { addr ->
                    if (addr is Inet6Address && !addr.isLinkLocalAddress && !addr.isLoopbackAddress) {
                        list.add(addr)
                    }
                }
            }
        } catch (e: Exception) {}
        return list
    }

    private fun startProxy(port: Int, bindIp: InetAddress?) {
        val server = ServerSocket(port)
        while (true) {
            val client = server.accept()
            thread {
                try {
                    val input = client.getInputStream()
                    val output = client.getOutputStream()

                    // SOCKS5 Greeting
                    input.read(); val nmethods = input.read()
                    input.readNBytes(nmethods)
                    output.write(byteArrayOf(0x05, 0x00))

                    // SOCKS5 Request
                    input.read(); val cmd = input.read(); input.read()
                    val atype = input.read()
                    
                    val address = when (atype) {
                        0x01 -> { // IPv4
                            val bytes = input.readNBytes(4)
                            InetAddress.getByAddress(bytes).hostAddress
                        }
                        0x03 -> { // Domain
                            val len = input.read()
                            String(input.readNBytes(len))
                        }
                        else -> return@thread
                    }
                    val portDest = ((input.read() and 0xff) shl 8) or (input.read() and 0xff)

                    val remote = Socket()
                    if (bindIp != null) remote.bind(InetSocketAddress(bindIp, 0))
                    remote.connect(InetSocketAddress(address, portDest), 10000)
                    
                    output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))

                    thread { remote.getInputStream().copyTo(output) }
                    input.copyTo(remote.getOutputStream())
                } catch (e: Exception) {
                } finally {
                    client.close()
                }
            }
        }
    }
}
