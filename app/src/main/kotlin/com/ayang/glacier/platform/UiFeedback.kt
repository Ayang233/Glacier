package com.ayang.glacier.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.getSystemService

/** 统一的轻量反馈出口，避免各页面自己拼 Toast / Intent。 */
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
