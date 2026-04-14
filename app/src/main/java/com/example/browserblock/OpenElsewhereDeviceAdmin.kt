package com.example.browserblock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * OpenElsewhereDeviceAdmin — [DeviceAdminReceiver] subclass.
 *
 * Declared in the manifest with:
 *   android:permission="android.permission.BIND_DEVICE_ADMIN"
 * and meta-data pointing to @xml/device_admin which declares <force-lock/>.
 *
 * Device Admin policy grants (see res/xml/device_admin.xml):
 *  - USES_POLICY_FORCE_LOCK — enables [android.app.admin.DevicePolicyManager.lockNow]
 *    as a hard-enforcement fallback when overlay-based blocking fails.
 *
 * "OpenElsewhere" naming rationale:
 *   The primary use of admin privileges is to redirect users to an allowed
 *   browser (open URL elsewhere) rather than to punish / wipe. The device
 *   admin lock is a last resort, not the primary mechanism.
 *
 * Activation: user must go to Settings → Security → Device Admin and enable
 * this receiver. [SetupActivity] will guide them with a direct Intent.
 *
 * Logic to be implemented in a later step.
 */
class OpenElsewhereDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // TODO: update AppPreferences.isDeviceAdminEnabled = true
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // TODO: update AppPreferences.isDeviceAdminEnabled = false
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_description)
    }
}
