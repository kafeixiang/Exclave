package io.nekohasekai.sagernet.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.Logs
import java.io.File

fun SagerNet.cleanWebview() {
    var pathToClean = "app_webview"
    if (isBgProcess) pathToClean += "_$process"
    try {
        val dataDir = filesDir.parentFile!!
        File(dataDir, "$pathToClean/BrowserMetrics").recreate(true)
        File(dataDir, "$pathToClean/BrowserMetrics-spare.pma").recreate(false)
    } catch (e: Exception) {
        Logs.e(e.toString())
    }
}

fun File.recreate(dir: Boolean) {
    if (parentFile?.isDirectory != true) return
    if (dir && !isFile) {
        if (exists()) deleteRecursively()
        createNewFile()
    } else if (!dir && !isDirectory) {
        if (exists()) delete()
        mkdir()
    }
}

// Context utils

@SuppressLint("DiscouragedApi")
fun Context.getDrawableByName(name: String?): Drawable? {
    val resourceId: Int = resources.getIdentifier(name, "drawable", packageName)
    return AppCompatResources.getDrawable(this, resourceId)
}