package com.ybridge.node

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Jalankan 5 slot proxy sekaligus
        val ports = listOf(2000, 2001, 2002, 2003, 2004)
        ports.forEach { port ->
            thread { startProxy(port) }
        }
    }

    private fun startProxy(port: Int) {
        val server = ServerSocket(port)
        while (true) {
            val client = server.accept()
            thread { handleClient(client) }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            // SOCKS5 Logic Simple
            client.getInputStream().read() // Version
            // ... (Kode SOCKS5 lengkap untuk Multi-IP IPv6)
            // Di sini nanti aplikasi akan otomatis memilih IPv6 yang berbeda
        } catch (e: Exception) {}
    }
}
