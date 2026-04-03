package com.range.rangeEmulator.util

import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress.contains(".")) {
                        return addr.hostAddress?:"0.0.0.0"
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }
}