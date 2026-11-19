package com.ayang.glacier.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
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
 * 三条来源，按优先级：图标包 → 系统图标 → 空占位。
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
            loadSystemIcon(info, sizePx)
        } ?: return null

        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * 系统图标。
     *
     * 自适应图标不能交给 `AdaptiveIconDrawable.draw()`：那会套用系统遮罩（AOSP 默认是圆形），
     * Glacier 拿到的是已经裁成圆形的位图，外面再套圆角矩形也没用 —— 主页与列表看起来就是圆形图标。
     * 这里自己画原始 108dp 画布（背景层 + 前景层），再由 UI 层套 MIUIX squircle 轮廓。
     */
    private fun loadSystemIcon(info: ApplicationInfo, sizePx: Int): Bitmap? {
        val adaptive = runCatching { info.loadIcon(context.packageManager) as? AdaptiveIconDrawable }
            .getOrNull()
        if (adaptive != null) {
            renderUnmasked(adaptive, sizePx)?.let { return it }
        }
        val loader = loaders.getOrPut(sizePx) {
            AppIconLoader(sizePx, synthesizeAdaptive, context)
        }
        return runCatching { loader.loadIcon(info, false) }.getOrNull()
    }

    /**
     * 按启动器遮罩语义渲染自适应图标（不套任何遮罩）。
     *
     * 自适应图标的完整画布是 108dp，可见遮罩区（启动器真正的图标形状）只有居中的 72dp；
     * 因此启动器实际显示时看到的始终是“108dp 画布的中心 2/3”。如果把 108dp 画布直接铺满
     * [sizePx] 位图，位图里就只有 2/3 是启动器上能看到的区域，图标内容看起来会偏小约 1/3。
     *
     * 这里按同一语义放大 1.5×：把 108dp 画布的 bounds 设为
     * `(-sizePx/4, -sizePx/4, sizePx*5/4, sizePx*5/4)`（每边外扩 sizePx/4，总宽 = 1.5×sizePx），
     * 使中心 72dp 安全区正好铺满 [sizePx]，再由 UI 层 `squircleClip` 裁出 MIUIX 轮廓。
     * 前景内容位于 72dp 安全区内，外扩不会裁掉安全区内容。
     *
     * 抗锯齿：Android `Canvas` 默认开启抗锯齿，VectorDrawable / BitmapDrawable 自带 AA 与双线性采样；
     * 输出位图为 ARGB_8888，初始像素全透明（未覆盖区域保持透明，不垫底色）。
     */
    private fun renderUnmasked(adaptive: AdaptiveIconDrawable, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        return runCatching {
            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            // 108dp 画布按 1.5× 绘制：中心 72dp 安全区 == sizePx，画布外扩 sizePx/4。
            val left = -sizePx / 4
            val top = left
            val right = sizePx * 5 / 4
            val bottom = right
            val background = adaptive.background
            val foreground = adaptive.foreground
            background?.setBounds(left, top, right, bottom)
            foreground?.setBounds(left, top, right, bottom)
            background?.draw(canvas)
            foreground?.draw(canvas)
            output
        }.getOrNull()?.takeIf { !it.isRecycled }
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
