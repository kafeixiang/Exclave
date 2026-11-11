package io.nekohasekai.sagernet.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import java.io.File
import java.io.FileOutputStream

object WallpaperManager {

    data class WallpaperItem(
        val id: String,
        val name: String,
        val path: String,
        val isPreset: Boolean,
        val drawableRes: Int = 0
    )

    const val PRESET_MESH = "preset:mesh"
    const val PRESET_AURORA = "preset:aurora"
    const val PRESET_SUNSET = "preset:sunset"
    const val PRESET_NEBULA = "preset:nebula"

    private fun getWallpaperDir(context: Context): File {
        val dir = File(context.filesDir, "wallpapers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getAllWallpapers(context: Context): List<WallpaperItem> {
        val list = mutableListOf<WallpaperItem>()

        // Add presets
        list.add(
            WallpaperItem(
                id = "preset_mesh",
                name = context.getString(R.string.wallpaper_mesh),
                path = PRESET_MESH,
                isPreset = true,
                drawableRes = R.drawable.bg_mesh_gradient
            )
        )
        list.add(
            WallpaperItem(
                id = "preset_aurora",
                name = context.getString(R.string.wallpaper_aurora),
                path = PRESET_AURORA,
                isPreset = true,
                drawableRes = R.drawable.bg_wallpaper_aurora
            )
        )
        list.add(
            WallpaperItem(
                id = "preset_sunset",
                name = context.getString(R.string.wallpaper_sunset),
                path = PRESET_SUNSET,
                isPreset = true,
                drawableRes = R.drawable.bg_wallpaper_sunset
            )
        )
        list.add(
            WallpaperItem(
                id = "preset_nebula",
                name = context.getString(R.string.wallpaper_nebula),
                path = PRESET_NEBULA,
                isPreset = true,
                drawableRes = R.drawable.bg_wallpaper_dark_nebula
            )
        )

        // Add uploaded custom wallpapers
        val dir = getWallpaperDir(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.extension.equals("jpg", true) ||
                    f.extension.equals("jpeg", true) ||
                    f.extension.equals("png", true) ||
                    f.extension.equals("webp", true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        files.forEachIndexed { index, file ->
            list.add(
                WallpaperItem(
                    id = file.name,
                    name = "${context.getString(R.string.custom_wallpaper)} ${index + 1}",
                    path = file.absolutePath,
                    isPreset = false
                )
            )
        }

        return list
    }

    fun saveWallpaper(context: Context, uri: Uri): WallpaperItem? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds to prevent OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Compute sample size (limit max dimension to 2560px)
            val maxDim = maxOf(origWidth, origHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 2560) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val dir = getWallpaperDir(context)
            val fileName = "wp_${System.currentTimeMillis()}.jpg"
            val targetFile = File(dir, fileName)

            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()

            val all = getAllWallpapers(context)
            all.find { it.path == targetFile.absolutePath }
                ?: WallpaperItem(
                    id = targetFile.name,
                    name = context.getString(R.string.custom_wallpaper),
                    path = targetFile.absolutePath,
                    isPreset = false
                )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteWallpaper(context: Context, path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists() && file.isFile) {
                file.delete()
                if (DataStore.currentWallpaperPath == path) {
                    DataStore.currentWallpaperPath = PRESET_MESH
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun loadWallpaperDrawable(context: Context, path: String): Drawable? {
        val effectivePath = if (path.isEmpty()) PRESET_MESH else path
        return when (effectivePath) {
            PRESET_MESH -> ContextCompat.getDrawable(context, R.drawable.bg_mesh_gradient)
            PRESET_AURORA -> ContextCompat.getDrawable(context, R.drawable.bg_wallpaper_aurora)
            PRESET_SUNSET -> ContextCompat.getDrawable(context, R.drawable.bg_wallpaper_sunset)
            PRESET_NEBULA -> ContextCompat.getDrawable(context, R.drawable.bg_wallpaper_dark_nebula)
            else -> {
                val file = File(effectivePath)
                if (file.exists() && file.isFile) {
                    try {
                        val metrics = context.resources.displayMetrics
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        val sampleSize = maxOf(
                            1,
                            minOf(options.outWidth / metrics.widthPixels, options.outHeight / metrics.heightPixels)
                        )
                        val loadOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath, loadOptions)
                        if (bitmap != null) {
                            BitmapDrawable(context.resources, bitmap)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    ContextCompat.getDrawable(context, R.drawable.bg_mesh_gradient)
                }
            }
        }
    }

    fun getThumbnail(context: Context, item: WallpaperItem, targetWidthPx: Int = 180, targetHeightPx: Int = 240): Drawable? {
        if (item.isPreset) {
            return ContextCompat.getDrawable(context, item.drawableRes)
        }
        val file = File(item.path)
        if (!file.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val sampleSize = maxOf(
                1,
                minOf(options.outWidth / targetWidthPx, options.outHeight / targetHeightPx)
            )
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, loadOptions)
            if (bitmap != null) {
                BitmapDrawable(context.resources, bitmap)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Applies the unified Cupertino / Wallpaper backdrop layer onto [container] + [bgImage] + [ambientGlow]
     * so MainActivity and sub-activities share identical visuals, scrim, and blur behavior.
     */
    fun applyWindowBackdrop(
        container: View,
        bgImage: ImageView,
        ambientGlow: View?,
        showMeshWhenDefault: Boolean,
    ) {
        val isNight = Theme.usingNightMode()
        val isWallpaper = Theme.isWallpaperTheme()
        val isPureBlack = isNight && DataStore.amoledPureBlack
        val blurRadius = DataStore.wallpaperBlurRadius.toFloat().coerceIn(0f, 80f)

        if (isPureBlack && !isWallpaper) {
            bgImage.setImageDrawable(null)
            bgImage.isVisible = false
            ambientGlow?.isVisible = false
            container.setBackgroundColor(Color.BLACK)
            applyBlurEffect(bgImage, 0f)
            return
        }

        if (isWallpaper) {
            val drawable = loadWallpaperDrawable(container.context, DataStore.currentWallpaperPath)
            if (drawable != null) {
                bgImage.isVisible = true
                bgImage.setImageDrawable(drawable)
                bgImage.alpha = if (isNight) 0.85f else 0.92f
                configureAmbientGlow(ambientGlow, isNight, container.context)
                container.setBackgroundColor(
                    if (isNight) Color.parseColor("#33000000") else Color.parseColor("#1AFFFFFF")
                )
                applyBlurEffect(bgImage, blurRadius)
                return
            }
        }

        if (showMeshWhenDefault) {
            bgImage.isVisible = true
            bgImage.setImageResource(R.drawable.bg_mesh_gradient)
            bgImage.alpha = if (isNight) 0.70f else 0.88f
            configureAmbientGlow(ambientGlow, isNight, container.context)
            container.setBackgroundResource(R.color.cupertino_window_bg)
            applyBlurEffect(bgImage, blurRadius)
        } else {
            bgImage.setImageDrawable(null)
            bgImage.isVisible = false
            ambientGlow?.isVisible = false
            container.setBackgroundColor(Theme.getClassicListBackgroundColor())
            applyBlurEffect(bgImage, 0f)
        }
    }

    private fun configureAmbientGlow(ambientGlow: View?, isNight: Boolean, context: Context) {
        if (ambientGlow == null) return
        if (isNight) {
            ambientGlow.alpha = 0f
            ambientGlow.visibility = View.GONE
            return
        }
        ambientGlow.backgroundTintList = ColorStateList.valueOf(Theme.getAccentColor(context))
        ambientGlow.alpha = 0.1f
        ambientGlow.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ambientGlow.setRenderEffect(
                RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.DECAL)
            )
        }
    }

    private fun applyBlurEffect(target: View, blurRadius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(
                if (blurRadius > 0f) {
                    RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        }
    }
}
