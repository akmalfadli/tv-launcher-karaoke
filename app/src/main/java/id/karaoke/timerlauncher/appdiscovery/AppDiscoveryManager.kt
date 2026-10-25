package id.karaoke.timerlauncher.appdiscovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isLeanback: Boolean,
    val isAllowed: Boolean,
    val iconBitmap: ImageBitmap? = null
)

class AppDiscoveryManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Mengambil daftar seluruh aplikasi yang dapat dibuka pada TV (Leanback launcher & Standard launcher).
     * Aplikasi launcher karaoke sendiri difilter keluar.
     */
    fun getInstalledApps(allowedPackages: Set<String>): List<InstalledAppItem> {
        val selfPackage = context.packageName
        val foundMap = mutableMapOf<String, InstalledAppItem>()

        // 1. Query Leanback Launcher apps (khusus Android TV)
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvApps = packageManager.queryIntentActivities(tvIntent, 0)
        for (resolveInfo in tvApps) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == selfPackage) continue

            val name = resolveInfo.loadLabel(packageManager).toString()
            val drawable = resolveInfo.loadIcon(packageManager)
            val bitmap = drawableToBitmap(drawable)?.asImageBitmap()

            foundMap[pkg] = InstalledAppItem(
                packageName = pkg,
                appName = name,
                isLeanback = true,
                isAllowed = allowedPackages.contains(pkg),
                iconBitmap = bitmap
            )
        }

        // 2. Query Standard Launcher apps (aplikasi non-TV yang sideloaded / standar)
        val standardIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val standardApps = packageManager.queryIntentActivities(standardIntent, 0)
        for (resolveInfo in standardApps) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == selfPackage) continue
            if (foundMap.containsKey(pkg)) continue

            val name = resolveInfo.loadLabel(packageManager).toString()
            val drawable = resolveInfo.loadIcon(packageManager)
            val bitmap = drawableToBitmap(drawable)?.asImageBitmap()

            foundMap[pkg] = InstalledAppItem(
                packageName = pkg,
                appName = name,
                isLeanback = false,
                isAllowed = allowedPackages.contains(pkg),
                iconBitmap = bitmap
            )
        }

        // Urutkan: Aplikasi yang diizinkan lebih dulu, lalu alfabetis
        return foundMap.values.sortedWith(
            compareByDescending<InstalledAppItem> { it.isAllowed }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Konversi drawable ikon aplikasi menjadi Android Bitmap untuk rendering Compose & Web.
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
