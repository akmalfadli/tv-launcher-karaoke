package id.karaoke.timerlauncher.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface

data class DeviceStatus(
    val ipAddress: String,
    val isOverlayPermissionGranted: Boolean,
    val isDefaultLauncher: Boolean,
    val isAdbEnabled: Boolean,
    val isBatteryOptimizationIgnored: Boolean
) {
    val warnings: List<String>
        get() {
            val list = mutableListOf<String>()
            if (!isOverlayPermissionGranted) {
                list.add("OVERLAY_DENIED")
            }
            if (!isDefaultLauncher) {
                list.add("NOT_DEFAULT_LAUNCHER")
            }
            if (isAdbEnabled) {
                list.add("ADB_ENABLED")
            }
            if (!isBatteryOptimizationIgnored) {
                list.add("BATTERY_OPTIMIZATION_ACTIVE")
            }
            return list
        }
}

object DeviceHealth {

    fun getDeviceStatus(context: Context): DeviceStatus {
        val ip = getIpAddress(context)
        val overlay = hasOverlayPermission(context)
        val launcher = isDefaultLauncher(context)
        val adb = isAdbEnabled(context)
        val battery = isIgnoringBatteryOptimizations(context)

        return DeviceStatus(
            ipAddress = ip,
            isOverlayPermissionGranted = overlay,
            isDefaultLauncher = launcher,
            isAdbEnabled = adb,
            isBatteryOptimizationIgnored = battery
        )
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun isAdbEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun getIpAddress(context: Context): String {
        try {
            // First try active WiFi
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) {
                val formatted = String.format(
                    "%d.%d.%d.%d",
                    wifiIp and 0xff,
                    wifiIp shr 8 and 0xff,
                    wifiIp shr 16 and 0xff,
                    wifiIp shr 24 and 0xff
                )
                if (formatted != "0.0.0.0") return formatted
            }

            // Fallback to NetworkInterfaces (Ethernet or WiFi)
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "127.0.0.1"
    }
}
