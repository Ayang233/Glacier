package com.ayang.glacier.platform.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R

/**
 * DeviceAdmin 接收器。
 *
 * 只用于"锁屏"这一项策略；设备所有者相关能力在 DeviceOwnerBackend 里，
 * 两者不混用，避免给了不该给的权限。
 */
class GlacierDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val app = context.applicationContext as? GlacierApplication ?: return
        app.freeze.deviceOwner.enableBackupServiceIfPossible()
        app.freeze.deviceOwner.setOrganizationNameIfPossible(context.getString(R.string.app_name))
    }
}
