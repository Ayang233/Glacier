package com.ayang.glacier.feature.api

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.app.GlacierApi
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.DEFAULT_TAG_ID
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.feature.common.AppActionRunner
import com.ayang.glacier.feature.common.AppActionSheet
import com.ayang.glacier.platform.PackageTools
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.theme.GlacierTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 对外 API / 深链 / 快捷方式入口（独立窗口、单实例）。
 *
 * 该窗口不共享主界面的录制层：这里自建一个整窗录制层，
 * 弹层采样本窗口这一层，坐标与主窗口完全隔离。
 */
class ApiActivity : ComponentActivity() {

    private var errorMessage by mutableStateOf<String?>(null)
    private var sheetEntry by mutableStateOf<AppEntry?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GlacierApplication
        setContent {
            GlacierTheme(mode = app.preferences.themeMode) {
                ApiRoot {
                    // 弹层常驻组合，只用 show 驱动；保留最后一次的内容供退场动画使用。
                    // （官方契约：浮层不允许用 `if (show)` 包裹，见 miuix-os4-contract.md 第 0 节红线 1。）
                    var lastEntry by remember { mutableStateOf<AppEntry?>(null) }
                    var lastError by remember { mutableStateOf<String?>(null) }
                    var actionChosen by remember { mutableStateOf(false) }
                    val entry = sheetEntry
                    if (entry != null) lastEntry = entry
                    if (errorMessage != null) lastError = errorMessage

                    AppActionSheet(
                        entry = lastEntry,
                        show = entry != null,
                        inHome = true,
                        onDismiss = { sheetEntry = null },
                        // 退场动画结束后再关窗；动作路径由业务协程自己 finish()，这里不抢跑。
                        onDismissFinished = { if (!actionChosen) finish() },
                        onAction = { action ->
                            val target = lastEntry
                            actionChosen = true
                            sheetEntry = null
                            if (target != null) {
                                lifecycleScope.launch {
                                    val result = runCatching {
                                        AppActionRunner(this@ApiActivity, app.repository)
                                            .run(target, action)
                                    }
                                    result.onFailure { showError(it) }
                                    // 成功即关窗；失败保留窗口，让下面的错误 Dialog 真的能被用户看到。
                                    if (result.isSuccess) finish()
                                }
                            }
                        },
                    )

                    GlassDialog(
                        onDismissRequest = { errorMessage = null },
                        show = errorMessage != null,
                        backdrop = LocalRootBackdrop.current,
                        title = stringResource(R.string.app_name),
                        summary = lastError,
                        // 官方参数：等退出动画结束再 finish()，否则窗口立即销毁、动画被砍掉。
                        onDismissFinished = { finish() },
                    ) {
                        GlassDialogButton(
                            onClick = { errorMessage = null },
                            role = GlassDialogButtonRole.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ok), style = MiuixTheme.textStyles.button)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            runCatching { execute(intent) }.onFailure { showError(it) }
        }
    }

    private suspend fun execute(intent: Intent) {
        val app = application as GlacierApplication
        val repository = app.repository
        if (repository.snapshot.all.isEmpty()) repository.refresh()

        when (val action = intent.action) {
            Intent.ACTION_SHOW_APP_INFO -> {
                val packageName = requirePackage(intent, showAppInfo = true)
                sheetEntry = repository.entryOf(packageName)
                    ?: AppEntry(
                        packageName = packageName,
                        label = PackageTools.labelOf(this, packageName).toString(),
                        isSystem = false,
                        isInstalled = true,
                        frozen = repository.freeze.isFrozen(packageName),
                        pinned = false,
                        whitelisted = false,
                        tagIds = listOf(DEFAULT_TAG_ID),
                        installTime = 0L,
                        updateTime = 0L,
                        search = com.ayang.glacier.domain.SearchKeys(packageName, packageName, packageName, ""),
                    )
            }

            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri?.scheme != GlacierApi.SCHEME) {
                    throw IllegalArgumentException(getString(R.string.api_unknown_scheme, uri?.scheme))
                }
                val mapped = GlacierApi.actionForHost(uri.host)
                    ?: throw IllegalArgumentException(getString(R.string.unknown_action, uri.host.orEmpty()))
                executeAction(mapped, uri)
                finish()
            }

            else -> {
                if (action == null) throw IllegalArgumentException(getString(R.string.unknown_action, "null"))
                executeAction(action, null)
                if (sheetEntry == null && errorMessage == null) finish()
            }
        }
    }

    private suspend fun executeAction(action: String, uri: Uri?) {
        val app = application as GlacierApplication
        val repository = app.repository
        when (action) {
            GlacierApi.ACTION_LAUNCH -> {
                val packageName = requirePackage(intent, uri)
                if (repository.freeze.isFrozen(packageName)) {
                    repository.setFrozen(packageName, false)
                    if (repository.selectedMode() == WorkingMode.IslandFreeze ||
                        repository.selectedMode() == WorkingMode.IslandSuspend
                    ) {
                        repository.freeze.island.awaitLaunchIntent(packageName)
                    }
                }
                val launch = PackageTools.launchIntent(this, packageName)
                    ?: throw ActivityNotFoundException(getString(R.string.activity_not_found))
                startActivity(launch)
            }

            GlacierApi.ACTION_FREEZE -> setFrozen(requirePackage(intent, uri), true)
            GlacierApi.ACTION_UNFREEZE -> setFrozen(requirePackage(intent, uri), false)

            GlacierApi.ACTION_FREEZE_TAG -> setListFrozen(
                requireTagId(intent, uri), frozen = true,
            )

            GlacierApi.ACTION_UNFREEZE_TAG -> setListFrozen(
                requireTagId(intent, uri), frozen = false,
            )

            GlacierApi.ACTION_FREEZE_ALL -> setListFrozen(null, frozen = true)
            GlacierApi.ACTION_UNFREEZE_ALL -> setListFrozen(null, frozen = false)
            GlacierApi.ACTION_FREEZE_NON_WHITELISTED -> setListFrozen(null, frozen = true, skipWhitelisted = true)

            GlacierApi.ACTION_FREEZE_AUTO -> {
                com.ayang.glacier.platform.auto.AutoFreezeScheduler.scheduleAutoFreeze(this, fromScreenOff = false)
                finish()
            }

            GlacierApi.ACTION_LOCK -> lockScreen(freezeAll = false)
            GlacierApi.ACTION_LOCK_FREEZE -> lockScreen(freezeAll = true)

            else -> throw IllegalArgumentException(getString(R.string.unknown_action, action))
        }
    }

    private suspend fun setFrozen(packageName: String, frozen: Boolean) {
        val repository = (application as GlacierApplication).repository
        if (frozen && !repository.store.isManaged(packageName)) {
            throw SecurityException(getString(R.string.package_not_checked))
        }
        if (repository.freeze.isFrozen(packageName) != frozen &&
            !repository.setFrozen(packageName, frozen)
        ) {
            throw IllegalStateException(getString(R.string.permission_denied))
        }
        UiFeedback.toast(
            this,
            if (frozen) R.string.msg_freeze else R.string.msg_unfreeze,
            PackageTools.labelOf(this, packageName),
        )
    }

    private suspend fun setListFrozen(
        tagId: Int?,
        frozen: Boolean,
        skipWhitelisted: Boolean = false,
    ) {
        val repository = (application as GlacierApplication).repository
        val pool = if (tagId == null) {
            repository.snapshot.home
        } else {
            repository.snapshot.home.filter { tagId in it.tagIds }
        }
        val targets = pool.filter { it.frozen != frozen && !(skipWhitelisted && it.whitelisted) }
            .map { it.packageName }
        val outcome = repository.setFrozenBatch(targets, frozen)
        when (outcome) {
            is com.ayang.glacier.system.FreezeOutcome.Applied -> {
                if (outcome.count == 0) {
                    throw IllegalStateException(getString(R.string.permission_denied))
                }
                UiFeedback.toast(
                    this,
                    if (frozen) R.string.msg_freeze_many else R.string.msg_unfreeze_many,
                    outcome.count.toString(),
                )
            }

            com.ayang.glacier.system.FreezeOutcome.Denied ->
                throw IllegalStateException(getString(R.string.permission_denied))
        }
    }

    private suspend fun lockScreen(freezeAll: Boolean) {
        val repository = (application as GlacierApplication).repository
        if (freezeAll) setListFrozen(null, frozen = true)
        if (!repository.lockScreen()) throw IllegalStateException(getString(R.string.permission_denied))
    }

    private fun requirePackage(intent: Intent, uri: Uri? = null, showAppInfo: Boolean = false): String {
        val raw = when {
            uri != null -> uri.getQueryParameter(GlacierApi.EXTRA_PACKAGE)
            showAppInfo -> intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            else -> intent.getStringExtra(GlacierApi.EXTRA_PACKAGE)
        }
        val packageName = raw ?: throw IllegalArgumentException(getString(R.string.api_missing_package))

        if (PackageTools.applicationInfoOrNull(this, packageName) == null) {
            throw NameNotFoundException(getString(R.string.app_not_installed))
        }
        return packageName
    }

    private fun requireTagId(intent: Intent, uri: Uri?): Int {
        val tag = if (uri != null) {
            uri.getQueryParameter(GlacierApi.EXTRA_TAG)
        } else {
            intent.getStringExtra(GlacierApi.EXTRA_TAG)
        } ?: throw IllegalArgumentException(getString(R.string.tag_unavailable, "null"))
        val repository = (application as GlacierApplication).repository
        return repository.snapshot.tags.firstOrNull { it.name == tag }?.id
            ?: throw IllegalStateException(getString(R.string.tag_unavailable, tag))
    }

    private fun showError(throwable: Throwable) {
        errorMessage = throwable.message ?: throwable.stackTraceToString()
    }

    /**
     * 独立窗口的 Glass 根：一个整窗录制层 + 最外层 Scaffold（承载弹层）。
     * 半透明底色画在录制层外侧，录制层内部始终是不透明 surface + 内容。
     */
    @Composable
    private fun ApiRoot(content: @Composable () -> Unit) {
        val app = application as GlacierApplication
        val glassActive = app.preferences.glassEnabled && isRuntimeShaderSupported()
        val surface = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }

        CompositionLocalProvider(
            LocalGlassActive provides glassActive,
            LocalRootBackdrop provides backdrop.takeIf { glassActive },
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(surface.copy(alpha = if (glassActive) 0.92f else 1f))
                        .then(if (glassActive) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    content()
                }
            }
        }
    }
}
