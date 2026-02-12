package com.velocitygate.service;

import com.velocitygate.cache.VelocityCache;

/**
 * Core threat analysis engine that implements multiple detection strategies to identify
 * malicious requests from headless browsers, automated crawlers, and AI agents.
 * <p>
 * This class employs a two-stage detection approach:
 * <ol>
 *   <li><strong>Signature Detection</strong>: Identifies known bot and headless browser User-Agent strings</li>
 *   <li><strong>Velocity Analysis</strong>: Detects anomalous request rates that exceed normal user behavior</li>
 * </ol>
 * <p>
 * Both detection methods must pass independently for a request to be classified as non-malicious.
 *
 * @since 1.0.0
 */
public class ThreatAnalyzer {

    private static final int VELOCITY_THRESHOLD = 50;

    private final VelocityCache velocityCache;

    /**
     * Constructs a ThreatAnalyzer with the specified VelocityCache dependency.
     *
     * @param velocityCache the cache instance for velocity tracking
     * @throws IllegalArgumentException if velocityCache is null
     */
    public ThreatAnalyzer(VelocityCache velocityCache) {
        this.velocityCache = velocityCache;
    }

    /**
     * Performs a comprehensive threat assessment on a request based on IP address and User-Agent.
     * <p>
     * This method executes all available detection strategies in sequence, returning
     * {@code true} on the first positive match (fail-fast approach).
     *
     * @param ip the client IP address
     * @param userAgent the User-Agent header value
     * @return {@code true} if the request is classified as malicious; {@code false} otherwise
     */
    public boolean isMalicious(String ip, String userAgent) {
        if (isHeadlessOrBot(userAgent)) {
            return true;
        }
        return isVelocityAnomaly(ip);
    }

    /**
     * Detects headless browsers and known bot User-Agents through signature matching.
     * <p>
     * This method performs case-insensitive substring matching against a comprehensive
     * list of known bot and headless browser identifiers. Null or blank User-Agents
     * are also classified as suspicious.
     *
     * @param userAgent the User-Agent header value
     * @return {@code true} if the User-Agent indicates headless browser or bot activity
     */
    private boolean isHeadlessOrBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }

        String lowerCaseUserAgent = userAgent.toLowerCase();

        String[] botSignatures = {
            "headlesschrome",
            "puppeteer",
            "selenium",
            "phantomjs",
            "chromium",
            "webdriver",
            "headless",
            "bot",
            "crawler",
            "spider",
            "scraper",
            "curl",
            "wget",
            "httpclient",
            "python",
            "java/",
            "node",
            "go-http-client",
            "axios",
            "requests",
            "urllib",
            "jsdom",
        };

        for (String signature : botSignatures) {
            if (lowerCaseUserAgent.contains(signature)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detects velocity anomalies by checking if an IP exceeds the request rate threshold.
     * <p>
     * The velocity threshold is defined as 50 requests per second. This method records
     * the current request and evaluates whether the IP has exceeded this limit within
     * the 1-second sliding window.
     *
     * @param ip the client IP address
     * @return {@code true} if velocity anomaly is detected
     */
    private boolean isVelocityAnomaly(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        int requestCount = velocityCache.recordRequest(ip);
        return requestCount > VELOCITY_THRESHOLD;
    }

    /**
     * Retrieves the current request velocity for the specified IP address.
     *
     * @param ip the client IP address
     * @return the number of requests from this IP within the last second
     */
    public int getCurrentVelocity(String ip) {
        return velocityCache.getRequestCount(ip);
    }

    /**
     * Clears all cached velocity data.
     * <p>
     * This method is intended for testing and administrative operations only.
     */
    public void resetCache() {
        velocityCache.clear();
    }
}
