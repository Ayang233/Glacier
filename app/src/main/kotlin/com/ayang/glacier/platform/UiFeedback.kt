package com.ayang.glacier.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.getSystemService

/**
 * 统一的轻量反馈出口，避免各页面自己拼 Toast / Intent。
 *
 * **为什么是系统 `Toast` 而不是 MIUIX `Snackbar`**（R11 评估过并明确保留）：
 * 1. `Snackbar` 必须有宿主（`SnackbarHostState` + `Scaffold(snackbarHost = …)`），而冻结相关
 *    的提示有相当一部分发生在 Worker / Receiver / 快捷方式等没有 Composition 的上下文里；
 * 2. 页面级浮层（`GlassSearchOverlay`、`GlassBottomSheet`）画在 Scaffold 之上，
 *    会在搜索结果里"点一行加入/移出主页"这类高频路径上把 Snackbar 盖住，反馈直接丢失；
 * 3. `Toast` 是**系统**的反馈件而不是自造 UI：在 HyperOS 上由系统按 OS 风格绘制，
 *    与 MIUIX 的观感并不冲突。
 *
 * 因此这里不接管视觉，只保证"所有轻量反馈只有一个出口、措辞一致"。
 */
object UiFeedback {

    fun toast(context: Context, text: CharSequence, long: Boolean = false) {
        Toast.makeText(
            context,
            text,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }

    fun toast(context: Context, @StringRes res: Int, long: Boolean = false) =
        toast(context, context.getString(res), long)

    fun toast(context: Context, @StringRes res: Int, arg: CharSequence, long: Boolean = false) =
        toast(context, context.getString(res, arg), long)

    fun copy(context: Context, label: CharSequence, text: String) {
        context.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun openLink(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    fun startSystemActivity(context: Context, action: String, uri: String): Boolean = runCatching {
        context.startActivity(
            Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
