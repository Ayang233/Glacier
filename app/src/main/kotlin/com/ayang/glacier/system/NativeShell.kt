package com.ayang.glacier.system

import android.util.Log
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val output: String?) {
    val ok: Boolean get() = code == 0
}

/**
 * 无 root 管理框架时的本地 shell 执行（仅用于 `su`）。
 *
 * 超时强制销毁，避免 su 弹窗被拒后线程永久阻塞。
 */
object NativeShell {

    private const val TAG = "GlacierShell"

    fun run(command: String, root: Boolean = true, timeoutMs: Long = 12_000): ShellResult = runCatching {
        val process = ProcessBuilder(if (root) "su" else "sh")
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { it.write(command.toByteArray()) }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            Log.w(TAG, "timeout: $command")
            return ShellResult(1, null)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        ShellResult(process.exitValue(), output)
    }.getOrElse {
        Log.w(TAG, "exec failed: $command", it)
        ShellResult(1, null)
    }

    fun currentUserId(): Int =
        run("am get-current-user").output?.trim()?.toIntOrNull() ?: 0
}
