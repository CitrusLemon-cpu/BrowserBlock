package com.example.browserblock

/**
 * WatchedApps — curated lists of browser package names.
 *
 * DEFAULT_BROWSERS:
 *   Packages that BrowserBlock should monitor out-of-the-box.
 *   Populated from the top Android browsers by market share.
 *   The user can add/remove packages at runtime (persisted in [AppPreferences]).
 *
 * NEVER_BLOCK:
 *   Packages that should NEVER be intercepted regardless of mode.
 *   Includes BrowserBlock itself and critical system packages.
 *
 * All sets are immutable vals — runtime additions live in [AppPreferences].
 */
object WatchedApps {

    /** Default set of browser packages to watch on first install. */
    val DEFAULT_BROWSERS: Set<String> = setOf(
        "com.android.chrome",                  // Google Chrome
        "com.chrome.beta",                     // Chrome Beta
        "com.chrome.dev",                      // Chrome Dev
        "com.chrome.canary",                   // Chrome Canary
        "org.mozilla.firefox",                 // Firefox
        "org.mozilla.firefox_beta",            // Firefox Beta
        "org.mozilla.fenix",                   // Firefox Nightly (Fenix)
        "com.microsoft.emmx",                  // Microsoft Edge
        "com.brave.browser",                   // Brave
        "com.brave.browser_beta",              // Brave Beta
        "com.opera.browser",                   // Opera
        "com.opera.browser.beta",              // Opera Beta
        "com.opera.mini.native",               // Opera Mini
        "com.samsung.android.app.sbrowser",    // Samsung Internet
        "com.samsung.android.app.sbrowser.beta",
        "com.UCMobile.intl",                   // UC Browser
        "com.vivaldi.browser",                 // Vivaldi
        "com.kiwibrowser.browser",             // Kiwi
        "com.duckduckgo.mobile.android",       // DuckDuckGo
        "com.sec.android.app.sbrowser",        // Samsung Internet (alt package)
        "mobi.mgeek.TunnyBrowser",            // Dolphin
        "com.cloudmosa.puffinFree",            // Puffin
        "org.adblockplus.browser",             // Adblock Browser
        "com.yandex.browser",                  // Yandex Browser
    )

    /**
     * Packages that must never be blocked.
     * Modify with caution — blocking system UI or launcher will brick the device.
     */
    val NEVER_BLOCK: Set<String> = setOf(
        "com.example.browserblock",            // this app
        "com.android.settings",               // system settings
        "com.android.systemui",               // system UI
        "com.android.launcher",               // stock launcher
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",       // Samsung launcher
        "com.miui.home",                       // MIUI launcher
        "com.huawei.android.launcher",        // Huawei launcher
    )
}
