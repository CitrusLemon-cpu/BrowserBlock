package com.example.browserblock

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.browserblock.databinding.ActivitySetupBinding
import com.google.android.material.button.MaterialButton

class SetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupBinding
    private lateinit var notifPermLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notifPermLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        if (AppPreferences.isSetupComplete && isAccessibilityGranted() && isOverlayGranted()) {
            navigateToMain()
            return
        }

        setupButtonListeners(notifPermLauncher)
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatuses()
    }

    private fun refreshAllStatuses() {
        updateCard(
            badge = binding.badgeAccessibility,
            button = binding.btnAccessibility,
            granted = isAccessibilityGranted(),
            tier = PermissionTier.REQUIRED
        )
        updateCard(
            badge = binding.badgeOverlay,
            button = binding.btnOverlay,
            granted = isOverlayGranted(),
            tier = PermissionTier.REQUIRED
        )
        updateCard(
            badge = binding.badgeUsageStats,
            button = binding.btnUsageStats,
            granted = isUsageStatsGranted(),
            tier = PermissionTier.RECOMMENDED
        )
        updateCard(
            badge = binding.badgeNotifListener,
            button = binding.btnNotifListener,
            granted = isNotificationListenerGranted(),
            tier = PermissionTier.RECOMMENDED
        )
        updateCard(
            badge = binding.badgeDeviceAdmin,
            button = binding.btnDeviceAdmin,
            granted = isDeviceAdminGranted(),
            tier = PermissionTier.RECOMMENDED
        )
        updateCard(
            badge = binding.badgePostNotif,
            button = binding.btnPostNotif,
            granted = isPostNotificationsGranted(),
            tier = PermissionTier.OPTIONAL
        )

        binding.cardPostNotif.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE

        binding.btnContinue.isEnabled = isAccessibilityGranted() && isOverlayGranted()
    }

    private enum class PermissionTier { REQUIRED, RECOMMENDED, OPTIONAL }

    private fun updateCard(
        badge: TextView,
        button: MaterialButton,
        granted: Boolean,
        tier: PermissionTier
    ) {
        if (granted) {
            badge.text = getString(R.string.badge_granted)
            badge.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_granted)
            button.isEnabled = false
            button.text = "✓"
        } else {
            button.isEnabled = true
            button.text = "Enable"
            when (tier) {
                PermissionTier.REQUIRED -> {
                    badge.text = getString(R.string.badge_required)
                    badge.background = ContextCompat.getDrawable(this, R.drawable.bg_badge_required)
                }

                PermissionTier.RECOMMENDED -> {
                    badge.text = getString(R.string.badge_recommended)
                    badge.background =
                        ContextCompat.getDrawable(this, R.drawable.bg_badge_recommended)
                }

                PermissionTier.OPTIONAL -> {
                    badge.text = getString(R.string.badge_optional)
                    badge.background =
                        ContextCompat.getDrawable(this, R.drawable.bg_badge_recommended)
                }
            }
        }
    }

    private fun isAccessibilityGranted(): Boolean {
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

    private fun isPostNotificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupButtonListeners(notifPermLauncher: ActivityResultLauncher<String>) {
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        binding.btnUsageStats.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.btnNotifListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnDeviceAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this@SetupActivity, OpenElsewhereDeviceAdmin::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_description)
                )
            }
            startActivity(intent)
        }

        binding.btnPostNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnContinue.setOnClickListener {
            AppPreferences.isSetupComplete = true
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
