package com.example.browserblock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.browserblock.databinding.ActivityAppDetailBinding
import com.google.android.material.button.MaterialButton

class AppDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        fun start(context: Context, packageName: String) {
            context.startActivity(
                Intent(context, AppDetailActivity::class.java).apply {
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                }
            )
        }
    }

    private lateinit var binding: ActivityAppDetailBinding
    private lateinit var targetPackageName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run { finish(); return }

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(targetPackageName, 0)
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            targetPackageName
        }
        title = appName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupModeSwitch()
        setupInAppBrowsingToggle()
        setupSafeDomainsSection()
    }

    override fun onResume() {
        super.onResume()
        refreshAllSections()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupModeSwitch() {
        val mode = AppPreferences.getBlockingMode(targetPackageName)
        binding.switchBlockingMode.isChecked = mode == BlockingMode.ALLOWLIST
        updateModeSwitchLabels(mode)

        binding.switchBlockingMode.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) BlockingMode.ALLOWLIST else BlockingMode.KEYWORD
            AppPreferences.setPackageBlockingMode(targetPackageName, newMode)
            updateModeSwitchLabels(newMode)
            refreshAllSections()
        }
    }

    private fun updateModeSwitchLabels(mode: BlockingMode) {
        binding.tvModeName.text = when (mode) {
            BlockingMode.KEYWORD -> getString(R.string.mode_name_keyword)
            BlockingMode.ALLOWLIST -> getString(R.string.mode_name_allowlist)
        }
        binding.tvModeDescription.text = when (mode) {
            BlockingMode.KEYWORD -> getString(R.string.mode_desc_keyword)
            BlockingMode.ALLOWLIST -> getString(R.string.mode_desc_allowlist)
        }
    }

    private fun refreshAllSections() {
        refreshSafeDomains()
        refreshRecentlyBlocked()
        refreshUserForcedBlocks()
        refreshAllowedActivities()
    }

    private fun setupInAppBrowsingToggle() {
        binding.switchBlockAllInapp.isChecked = AppPreferences.isInAppBrowsingBlocked(targetPackageName)
        binding.switchBlockAllInapp.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setInAppBrowsingBlocked(targetPackageName, isChecked)
            refreshSafeDomains()
        }
    }

    private fun setupSafeDomainsSection() {
        binding.btnAddSafeDomain.setOnClickListener {
            val domain = binding.etSafeDomain.text?.toString().orEmpty().trim()
            if (domain.isEmpty()) return@setOnClickListener
            AppPreferences.addUserSafeDomain(targetPackageName, domain)
            binding.etSafeDomain.text?.clear()
            refreshSafeDomains()
        }
    }

    private fun refreshSafeDomains() {
        val blockAllEnabled = AppPreferences.isInAppBrowsingBlocked(targetPackageName)
        binding.containerSafeDomains.visibility = if (blockAllEnabled) View.VISIBLE else View.GONE
        if (!blockAllEnabled) return

        val container = binding.containerSafeDomainsList
        container.removeAllViews()

        val curated = (WatchedApps.curatedSafeDomains[targetPackageName] ?: emptySet()).sorted()
        val userDomains = AppPreferences.getUserSafeDomains(targetPackageName)
            .filter { it !in curated.toSet() }
            .sorted()

        curated.forEach { domain ->
            addRow(container, domain, getString(R.string.action_curated), clickable = false)
        }
        userDomains.forEach { domain ->
            addRow(container, domain, getString(R.string.action_remove)) {
                AppPreferences.removeUserSafeDomain(targetPackageName, domain)
                refreshSafeDomains()
            }
        }
    }

    private fun refreshRecentlyBlocked() {
        val container = binding.containerRecentlyBlocked
        container.removeAllViews()

        val blockedLog = AppPreferences.getRecentlyBlockedActivities(targetPackageName)
        val userAllowed = AppPreferences.getUnblockedActivities(targetPackageName)
        val curated = WatchedApps.curatedAllowedActivities[targetPackageName] ?: emptySet()
        val toShow = blockedLog.filter { it !in userAllowed && it !in curated }

        binding.headerRecentlyBlocked.visibility = if (toShow.isEmpty()) View.GONE else View.VISIBLE
        container.visibility = if (toShow.isEmpty()) View.GONE else View.VISIBLE
        toShow.forEach { className ->
            addRow(container, className, getString(R.string.action_allow)) {
                AppPreferences.addUnblockedActivity(targetPackageName, className)
                refreshAllSections()
            }
        }
    }

    private fun refreshUserForcedBlocks() {
        val container = binding.containerUserBlocks
        container.removeAllViews()

        val userBlocked = AppPreferences.getUserBlockedActivities(targetPackageName)

        binding.headerUserBlocks.visibility = if (userBlocked.isEmpty()) View.GONE else View.VISIBLE
        container.visibility = if (userBlocked.isEmpty()) View.GONE else View.VISIBLE
        userBlocked.forEach { className ->
            addRow(container, className, getString(R.string.action_remove)) {
                AppPreferences.removeUserBlockedActivity(targetPackageName, className)
                refreshAllSections()
            }
        }
    }

    private fun refreshAllowedActivities() {
        val container = binding.containerAllowed
        container.removeAllViews()

        val curated = WatchedApps.curatedAllowedActivities[targetPackageName] ?: emptySet()
        val userAllowed = AppPreferences.getUnblockedActivities(targetPackageName)
        val allAllowed = curated + userAllowed

        binding.headerAllowed.visibility = if (allAllowed.isEmpty()) View.GONE else View.VISIBLE
        container.visibility = if (allAllowed.isEmpty()) View.GONE else View.VISIBLE

        curated.sorted().forEach { className ->
            addRow(container, className, getString(R.string.action_curated), clickable = false)
        }

        userAllowed.filter { it !in curated }.sorted().forEach { className ->
            addRow(container, className, getString(R.string.action_reblock)) {
                AppPreferences.removeUnblockedActivity(targetPackageName, className)
                refreshAllSections()
            }
        }
    }

    private fun addRow(
        container: LinearLayout,
        className: String,
        actionLabel: String,
        clickable: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        val row = layoutInflater.inflate(R.layout.item_activity_entry, container, false)
        row.findViewById<TextView>(R.id.tv_class_name).text = className
        val button = row.findViewById<MaterialButton>(R.id.btn_action)
        button.text = actionLabel
        if (clickable && onClick != null) {
            button.setOnClickListener { onClick() }
        } else {
            button.isEnabled = false
            button.alpha = 0.4f
        }
        container.addView(row)
    }
}
