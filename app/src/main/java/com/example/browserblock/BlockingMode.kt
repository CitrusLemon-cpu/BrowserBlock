package com.example.browserblock

/**
 * BlockingMode — how BrowserBlock decides what to block.
 *
 * KEYWORD:
 *   Block any URL whose host or path contains one of the user-configured
 *   keyword strings. Flexible but can have false positives.
 *   Example: keyword "reddit" blocks reddit.com, old.reddit.com, i.reddit.com.
 *
 * ALLOWLIST:
 *   Block everything EXCEPT URLs that are explicitly on the user's allowlist.
 *   Strict mode — ideal for focus sessions where only a handful of sites
 *   are permitted.
 *   Example: allowlist = ["docs.google.com", "github.com"] → all other URLs
 *   are blocked regardless of their content.
 */
enum class BlockingMode {
    KEYWORD,
    ALLOWLIST,
}
