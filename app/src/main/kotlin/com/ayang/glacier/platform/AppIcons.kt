package com.ayang.glacier.platform

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader

/**
 * 应用图标加载。
 *
 * 三条来源，按优先级：图标包 → 系统图标（AppIconLoader 负责自适应遮罩）→ 默认图标。
 * 全部走 IO 线程 + LRU，组合期不读磁盘。
 */
class AppIconSource(
    private val context: Context,
    private val iconPack: String?,
    private val synthesizeAdaptive: Boolean,
) {
    private val maxSizeKb = (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val loaders = HashMap<Int, AppIconLoader>()

    fun load(packageName: String, sizePx: Int): Bitmap? {
        val key = "$packageName@$sizePx"
        cache.get(key)?.let { return it }

        val fromPack = iconPack?.let { pack ->
            runCatching { IconPackSource.loadIcon(context, pack, packageName) }.getOrNull()
        }
        val bitmap = fromPack ?: run {
            val info = PackageTools.applicationInfoOrNull(context, packageName) ?: return null
            val loader = loaders.getOrPut(sizePx) {
                AppIconLoader(sizePx, synthesizeAdaptive, context)
            }
            runCatching { loader.loadIcon(info, false) }.getOrNull()
        } ?: return null

        cache.put(key, bitmap)
        return bitmap
    }

    fun clear() {
        cache.evictAll()
        loaders.clear()
    }

    companion object {
        private var instance: AppIconSource? = null

        fun get(context: Context, iconPack: String?, synthesizeAdaptive: Boolean): AppIconSource {
            val cached = instance
            if (cached != null && cached.iconPack == iconPack && cached.synthesizeAdaptive == synthesizeAdaptive) {
                return cached
            }
            return AppIconSource(context.applicationContext, iconPack, synthesizeAdaptive).also { instance = it }
        }

        fun desaturate(source: Bitmap): Bitmap {
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            Canvas(output).drawBitmap(source, 0f, 0f, paint)
            return output
        }
    }
}

/** 图标包（ADW/Nova 规范的 appfilter.xml）读取。 */
private object IconPackSource {

    fun loadIcon(context: Context, packName: String, packageName: String): Bitmap? {
        val resources: Resources = context.packageManager.getResourcesForApplication(packName)
        val drawableName = resolveDrawableName(resources, packName, packageName) ?: return null
        val id = resources.getIdentifier(drawableName, "drawable", packName)
        if (id == 0) return null
        return BitmapFactory.decodeResource(resources, id)
    }

    private fun resolveDrawableName(
        resources: Resources,
        packName: String,
        packageName: String,
    ): String? {
        val filterId = resources.getIdentifier("appfilter", "xml", packName)
        if (filterId == 0) return null
        val parser: XmlResourceParser = resources.getXml(filterId)
        while (parser.eventType != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType == XmlResourceParser.START_TAG &&
                parser.attributeCount >= 2 &&
                parser.getAttributeValue(0)?.contains(packageName) == true
            ) {
                return parser.getAttributeValue(1)
            }
            parser.next()
        }
        return null
    }
}

/**
 * 组合期图标入口。返回 `null` 表示仍在加载或没有图标，调用方自行给占位。
 */
@Composable
fun rememberAppIcon(
    packageName: String,
    size: Dp,
    grayscale: Boolean,
    iconPack: String?,
    synthesizeAdaptive: Boolean,
): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val source = AppIconSource.get(context, iconPack, synthesizeAdaptive)

    val state by produceState<ImageBitmap?>(initialValue = null, packageName, sizePx, grayscale, iconPack, synthesizeAdaptive) {
        value = withContext(Dispatchers.IO) {
            val bitmap = source.load(packageName, sizePx) ?: return@withContext null
            val result = if (grayscale) AppIconSource.desaturate(bitmap) else bitmap
            result.asImageBitmap()
        }
    }
    return state
}
