package com.example.browserblock

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.browserblock.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDebugSwitch()
        setupResetButton()
        setupPermissionButtons()
        setupTroubleshootingButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshAllBadges()
    }

    private fun refreshAllBadges() {
        applyBadge(binding.badgeAccessibility, isAccessibilityEnabled(), required = true)
        applyBadge(binding.badgeOverlay, isOverlayGranted(), required = true)
        applyBadge(binding.badgeUsage, isUsageStatsGranted(), required = false)
        applyBadge(binding.badgeNotifListener, isNotificationListenerGranted(), required = false)
        applyBadge(binding.badgeDeviceAdmin, isDeviceAdminGranted(), required = false)

        applyServiceBadge(binding.badgeServiceAccessibility, BlockerAccessibilityService.instance != null)
        applyServiceBadge(binding.badgeServicePolling, ForegroundPollingService.instance != null)
    }

    private fun applyBadge(view: TextView, granted: Boolean, required: Boolean) {
        if (granted) {
            view.text = getString(R.string.settings_badge_active)
            view.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_granted)
        } else if (required) {
            view.text = getString(R.string.settings_badge_missing)
            view.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_required)
        } else {
            view.text = getString(R.string.settings_badge_optional)
            view.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_recommended)
        }
    }

    private fun applyServiceBadge(view: TextView, running: Boolean) {
        if (running) {
            view.text = getString(R.string.settings_badge_active)
            view.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_granted)
        } else {
            view.text = getString(R.string.settings_badge_missing)
            view.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_required)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(this, BlockerAccessibilityService::class.java).flattenToString()
        val targetShort =
            ComponentName(this, BlockerAccessibilityService::class.java).flattenToShortString()
        return enabled.split(":").any {
            it.equals(target, ignoreCase = true) || it.equals(targetShort, ignoreCase = true)
        }
    }

    private fun isOverlayGranted(): Boolean = Settings.canDrawOverlays(this)

    private fun isUsageStatsGranted(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val target = ComponentName(this, BlockerNotificationListenerService::class.java)
        return flat.split(":")
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == target.packageName && it.className == target.className }
    }

    private fun isDeviceAdminGranted(): Boolean {
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isAdminActive(ComponentName(this, OpenElsewhereDeviceAdmin::class.java))
    }

    private fun openOemAutoStartSettings() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = when {
            manufacturer.contains("samsung") -> listOf(
                Intent().setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                ),
                Intent().setClassName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> listOf(
                Intent().setClassName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HideAppsContainerManagementActivity"
                ),
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            )
            manufacturer.contains("oneplus") -> listOf(
                Intent().setClassName(
                    "com.oneplus.security",
                    "com.oneplus.security.OPMainActivity"
                )
            )
            manufacturer.contains("oppo") -> listOf(
                Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            )
            manufacturer.contains("vivo") -> listOf(
                Intent().setClassName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            )
            else -> emptyList()
        }

        val launched = intents.any { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (!launched) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun setupPermissionButtons() {
        binding.btnFixAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnFixOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnFixUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnFixNotifListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnFixDeviceAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this@SettingsActivity, OpenElsewhereDeviceAdmin::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_description)
                )
            }
            startActivity(intent)
        }
    }

    private fun setupTroubleshootingButtons() {
        binding.btnFixBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        binding.btnFixOemAutostart.setOnClickListener {
            openOemAutoStartSettings()
        }
    }

    private fun setupDebugSwitch() {
        binding.switchDebugMode.isChecked = AppPreferences.isDebugMode
        binding.switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.isDebugMode = isChecked
        }
    }

    private fun setupResetButton() {
        binding.btnReset.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_reset_title)
                .setMessage(R.string.settings_reset_message)
                .setNegativeButton(R.string.settings_reset_cancel, null)
                .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                    AppPreferences.resetAll()
                    val intent = Intent(this, SetupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                }
                .show()
        }
    }
}
