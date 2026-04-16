package com.example.browserblock

import android.view.accessibility.AccessibilityNodeInfo

object WebViewUrlScanner {

    private const val MAX_SCAN_DEPTH = 30

    private val webViewClassMarkers = setOf(
        "WebView",
        "XWalkView",
        "TbsWebView",
        "X5WebView",
    )

    private val urlPattern = Regex(
        """(https?://[^\s"'<>]+)""",
        RegexOption.IGNORE_CASE
    )

    data class UrlKeywordMatch(
        val url: String,
        val keyword: String,
    )

    fun extractUrls(root: AccessibilityNodeInfo?): Set<String> {
        root ?: return emptySet()
        val urls = mutableSetOf<String>()
        scanNode(root, urls, insideWebView = false, depth = 0)
        return urls
    }

    fun matchesBlockedKeyword(urls: Set<String>, blockedKeywords: Set<String>): Boolean {
        return findBlockedMatch(urls, blockedKeywords) != null
    }

    fun findBlockedMatch(urls: Set<String>, blockedKeywords: Set<String>): UrlKeywordMatch? {
        if (urls.isEmpty() || blockedKeywords.isEmpty()) return null
        val normalizedKeywords = blockedKeywords
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (normalizedKeywords.isEmpty()) return null

        urls.forEach { url ->
            val lowerUrl = url.lowercase()
            normalizedKeywords.firstOrNull { keyword -> lowerUrl.contains(keyword) }?.let { keyword ->
                return UrlKeywordMatch(url = url, keyword = keyword)
            }
        }
        return null
    }

    private fun scanNode(
        node: AccessibilityNodeInfo,
        urls: MutableSet<String>,
        insideWebView: Boolean,
        depth: Int,
    ) {
        if (depth > MAX_SCAN_DEPTH) return

        val className = node.className?.toString().orEmpty()
        val isWebView = webViewClassMarkers.any { marker ->
            className.contains(marker, ignoreCase = true)
        }
        val effectivelyInsideWebView = insideWebView || isWebView

        if (effectivelyInsideWebView) {
            node.text?.toString()?.let { extractUrlsFromText(it, urls) }
            node.contentDescription?.toString()?.let { extractUrlsFromText(it, urls) }
        }

        val viewId = node.viewIdResourceName.orEmpty()
        if (viewId.contains("url", ignoreCase = true) ||
            viewId.contains("address", ignoreCase = true) ||
            viewId.contains("location", ignoreCase = true)
        ) {
            node.text?.toString()?.let { extractUrlsFromText(it, urls) }
            node.contentDescription?.toString()?.let { extractUrlsFromText(it, urls) }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                scanNode(child, urls, effectivelyInsideWebView, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun extractUrlsFromText(text: String, urls: MutableSet<String>) {
        urlPattern.findAll(text).forEach { match ->
            urls.add(match.value)
        }
        val candidate = text.trim()
        if (candidate.contains(".") && !candidate.contains(" ") && candidate.length < 500) {
            urls.add(candidate)
        }
    }
}
