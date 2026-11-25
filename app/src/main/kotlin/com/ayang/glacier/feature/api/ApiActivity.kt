package com.ayang.glacier.feature.api

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.app.GlacierApi
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.DEFAULT_TAG_ID
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.feature.common.AppAction
import com.ayang.glacier.feature.common.AppActionRunner
import com.ayang.glacier.feature.common.AppActionSheet
import com.ayang.glacier.feature.common.apiMenuActions
import com.ayang.glacier.platform.PackageTools
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.theme.GlacierTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
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

    /** 操作单对应的应用是否在主页；决定 "Add to Home / Remove from Home" 的文案与语义。 */
    private var sheetInHome by mutableStateOf(true)

    /**
     * 深链/动作解析期间的加载态。
     *
     * 解析时要先 `repository.refresh()`，在慢设备上会有几百毫秒的空白窗口；
     * 这里按官方 `InfiniteProgressIndicator`（`miuix-ui/.../basic/ProgressIndicator.kt:303`，
     * 成熟用法见 InstallerX `LoadingContent.kt:20-35`）给一个居中加载态，
     * 而不是让用户面对一个"什么都没有"的窗口。
     */
    private var loading by mutableStateOf(true)

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
                        // 深链 / 快捷方式入口没有页面级 UI：标签编辑与定时任务都要落在页面里，
                        // 所以这份菜单不给这两项（清单见 `apiMenuActions`）。
                        actions = lastEntry?.let { apiMenuActions(it, sheetInHome) } ?: emptyList(),
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
                        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
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

                    // 加载态只在"还没有任何内容可显示"时出现；弹层/错误弹出来之后它自动让位。
                    if (loading && entry == null && errorMessage == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            runCatching { execute(intent) }.onFailure { showError(it) }
            loading = false
        }
    }

    private suspend fun execute(intent: Intent) {
        val app = application as GlacierApplication
        val repository = app.repository
        if (repository.snapshot.all.isEmpty()) repository.refresh()

        when (val action = intent.action) {
            Intent.ACTION_SHOW_APP_INFO -> {
                val packageName = requirePackage(intent, showAppInfo = true)
                // 操作单的 "Add to Home / Remove from Home" 依赖真实的主页归属：
                // 深链过来的应用大多不在主页，旧实现固定 inHome = true 会把文案写成
                // "从主页移除"，与实际行为相反。这里按快照里的 home 列表如实传入。
                sheetInHome = repository.snapshot.home.any { it.packageName == packageName }
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
                    // 与页面入口同一语义：冻结态先解冻再启动；解冻失败必须抛出去，
                    // 否则用户只会看到"应用打不开"，而不是"没权限解冻"。
                    if (!repository.setFrozen(packageName, false)) {
                        throw IllegalStateException(getString(R.string.permission_denied))
                    }
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
        // 弹层 summary 只放一句用户能读懂的话。不要把 stackTraceToString() 塞进 UI：
        // 一是 GlassDialog 的 summary 不截断，2.0x 字体下整段堆栈会把对话框撑出屏幕；
        // 二是堆栈属于开发信息，用户看懂的是 message（业务侧 message 已经是本地化字符串）。
        errorMessage = throwable.message?.takeIf { it.isNotBlank() }
            ?: throwable::class.java.simpleName
    }

    /**
     * 独立窗口的 Glass 根：一个整窗录制层 + 最外层 Scaffold（承载弹层）。
     *
     * 采样语义（与主窗口的 R 层对齐）：
     * - 录制层 `backdrop` 先 `drawRect(surface)` 再录内容，录制层永远有完整像素；
     * - 窗口本身也必须是**不透明**的：旧实现用 `surface.copy(alpha = 0.92f)` 当窗口底色，
     *   屏幕上透出的那 8% 系统背景并不在录制层里，"可见像素 ≠ 录制像素"，
     *   玻璃采样源天生与观感不一致（`miuix-glass/Glass.kt:49-50` 只允许采样本窗口子树）。
     *   官方没有"半透明独立窗口"的窗口级采样范式，因此这里选择让两者相等：
     *   `Scaffold.containerColor = surface`，正文录制层录到的正是屏幕上同一块 surface + 内容。
     * - 弹层仍由根 Scaffold 的 popup 宿主绘制，天然在录制层之外，不会自采样。
     */
    @Composable
    private fun ApiRoot(content: @Composable () -> Unit) {
        val app = application as GlacierApplication
        // 读的是 Compose 状态（GlacierPreferences.glassEnabled），开关切换会正常重组。
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
                containerColor = surface,
                contentWindowInsets = WindowInsets(0),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (glassActive) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    content()
                }
            }
        }
    }
}
