package com.ayang.glacier.app

import android.content.Intent
import com.ayang.glacier.BuildConfig

/**
 * Glacier 的对外 API 契约（Intent Action 与 URI Scheme）。
 *
 * 这些都是**系统级声明**：Action 里带 applicationId，所以 debug 与 release
 * 天然是两个独立命名空间，不会互相干扰。
 */
object GlacierApi {

    private val id: String get() = BuildConfig.APPLICATION_ID

    val ACTION_LAUNCH: String get() = "$id.action.LAUNCH"
    val ACTION_FREEZE: String get() = "$id.action.FREEZE"
    val ACTION_UNFREEZE: String get() = "$id.action.UNFREEZE"
    val ACTION_FREEZE_TAG: String get() = "$id.action.FREEZE_TAG"
    val ACTION_UNFREEZE_TAG: String get() = "$id.action.UNFREEZE_TAG"
    val ACTION_FREEZE_ALL: String get() = "$id.action.FREEZE_ALL"
    val ACTION_UNFREEZE_ALL: String get() = "$id.action.UNFREEZE_ALL"
    val ACTION_FREEZE_NON_WHITELISTED: String get() = "$id.action.FREEZE_NON_WHITELISTED"
    val ACTION_FREEZE_AUTO: String get() = "$id.action.FREEZE_AUTO"
    val ACTION_LOCK: String get() = "$id.action.LOCK"
    val ACTION_LOCK_FREEZE: String get() = "$id.action.LOCK_FREEZE"

    const val SCHEME = "glacier"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_TAG = "tag"

    fun forPackage(action: String, packageName: String): Intent =
        Intent(action).putExtra(EXTRA_PACKAGE, packageName)

    fun forTag(action: String, tag: String): Intent =
        Intent(action).putExtra(EXTRA_TAG, tag)

    /** Deep link host → Action。返回 null 表示 host 不合法。 */
    fun actionForHost(host: String?): String? = when (host) {
        "launch" -> ACTION_LAUNCH
        "freeze" -> ACTION_FREEZE
        "unfreeze" -> ACTION_UNFREEZE
        "freeze_tag" -> ACTION_FREEZE_TAG
        "unfreeze_tag" -> ACTION_UNFREEZE_TAG
        "freeze_all" -> ACTION_FREEZE_ALL
        "unfreeze_all" -> ACTION_UNFREEZE_ALL
        "freeze_non_whitelisted" -> ACTION_FREEZE_NON_WHITELISTED
        "freeze_auto" -> ACTION_FREEZE_AUTO
        "lock" -> ACTION_LOCK
        "lock_freeze" -> ACTION_LOCK_FREEZE
        else -> null
    }
}
