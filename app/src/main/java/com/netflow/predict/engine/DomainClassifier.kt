package com.netflow.predict.engine

import com.netflow.predict.data.model.DomainCategory

/**
 * Classifies domains into categories based on known blocklists
 * and heuristic pattern matching.
 *
 * In a production app, this would use a downloaded blocklist database
 * (e.g., EasyList, Steven Black's hosts, Disconnect tracking protection list).
 * For v1, we use embedded pattern matching.
 */
object DomainClassifier {

    /** Known tracker / ad domain patterns */
    private val trackerPatterns = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adnxs.com",
        "adsrvr.org",
        "facebook.com/tr",
        "pixel.facebook.com",
        "analytics.facebook.com",
        "graph.facebook.com",
        "ads.facebook.com",
        "an.facebook.com",
        "app-measurement.com",
        "firebase-settings.crashlytics.com",
        "crashlytics.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "amplitude.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "mparticle.com",
        "kochava.com",
        "applovin.com",
        "unity3d.com",
        "unityads.unity3d.com",
        "moat.com",
        "ias.com",
        "scorecardresearch.com",
        "quantserve.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "pubmatic.com",
        "openx.net",
        "rubiconproject.com",
        "smaato.net",
        "inmobi.com",
        "chartbeat.com",
        "hotjar.com",
        "mouseflow.com",
        "fullstory.com",
        "smartlook.com",
        "bugsnag.com",
        "sentry.io",
        "newrelic.com",
        "nr-data.net",
        "datadog.com",
        "clarity.ms"
    )

    /** Known ad-serving domain patterns */
    private val adPatterns = listOf(
        "ads.",
        "ad.",
        "adserver.",
        "adservice.",
        "adtech.",
        "advertising.",
        "banner.",
        "pagead.",
        "serving-sys.com",
        "adcolony.com",
        "mopub.com",
        "admob.com",
        "startapp.com",
        "vungle.com",
        "chartboost.com",
        "ironsrc.com",
        "is.com",
        "tapjoy.com",
        "fyber.com"
    )

    /** Known CDN domain patterns */
    private val cdnPatterns = listOf(
        "akamaized.net",
        "akamai.net",
        "cloudfront.net",
        "cloudflare.com",
        "cloudflare-dns.com",
        "cdn.",
        "static.",
        "assets.",
        "media.",
        "images.",
        "gstatic.com",
        "googleapis.com",
        "ggpht.com",
        "fbcdn.net",
        "cdninstagram.com",
        "twimg.com",
        "fastly.net",
        "edgecastcdn.net",
        "azureedge.net",
        "azure.com",
        "amazonaws.com",
        "s3.amazonaws.com"
    )

    /** Known suspicious patterns */
    private val suspiciousPatterns = listOf(
        "data-harvest",
        "tracker-",
        "-tracker.",
        "spy",
        "collect.",
        "telemetry.",
        "beacon.",
        "pixel.",
        "log.",
        "stats.",
        "metric.",
        "exfil",
        "phish",
        "malware"
    )

    /** Trusted first-party service patterns */
    private val trustedPatterns = listOf(
        "google.com",
        "googleapis.com",
        "gstatic.com",
        "apple.com",
        "icloud.com",
        "microsoft.com",
        "windows.com",
        "live.com",
        "amazon.com",
        "whatsapp.net",
        "whatsapp.com",
        "signal.org",
        "telegram.org"
    )

    /**
     * Classify a domain name into a category.
     * Priority: SUSPICIOUS > TRACKING > ADS > CDN > TRUSTED > UNKNOWN
     */
    fun classify(domain: String): DomainCategory {
        val lower = domain.lowercase()

        // Check suspicious first (highest priority)
        if (suspiciousPatterns.any { lower.contains(it) }) {
            return DomainCategory.SUSPICIOUS
        }

        // Check trackers
        if (trackerPatterns.any { lower.contains(it) || lower.endsWith(it) }) {
            return DomainCategory.TRACKING
        }

        // Check ads
        if (adPatterns.any { lower.contains(it) || lower.startsWith(it) }) {
            return DomainCategory.ADS
        }

        // Check CDN
        if (cdnPatterns.any { lower.contains(it) || lower.endsWith(it) }) {
            return DomainCategory.CDN
        }

        // Check trusted
        if (trustedPatterns.any { lower.endsWith(it) }) {
            return DomainCategory.TRUSTED
        }

        return DomainCategory.UNKNOWN
    }

    /**
     * Calculate a reputation score for a domain.
     * Returns 0.0 (very risky) to 1.0 (very safe).
     */
    fun reputationScore(domain: String): Float {
        return when (classify(domain)) {
            DomainCategory.TRUSTED -> 0.9f
            DomainCategory.CDN -> 0.8f
            DomainCategory.UNKNOWN -> 0.5f
            DomainCategory.ADS -> 0.3f
            DomainCategory.TRACKING -> 0.2f
            DomainCategory.SUSPICIOUS -> 0.1f
        }
    }
}
