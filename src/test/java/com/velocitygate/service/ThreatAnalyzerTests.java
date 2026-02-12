package com.velocitygate.service;

import com.velocitygate.cache.VelocityCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThreatAnalyzer.
 *
 * @since 1.0.0
 */
@DisplayName("ThreatAnalyzer Tests")
class ThreatAnalyzerTests {

    private ThreatAnalyzer threatAnalyzer;
    private VelocityCache velocityCache;

    @BeforeEach
    void setUp() {
        velocityCache = new VelocityCache();
        velocityCache.clear();
        threatAnalyzer = new ThreatAnalyzer(velocityCache);
    }

    @Test
    @DisplayName("Detects HeadlessChrome user agent")
    void testDetectHeadlessChrome() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", 
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 HeadlessChrome/120.0");
        assertTrue(result);
    }

    @Test
    @DisplayName("Detects Puppeteer user agent")
    void testDetectPuppeteer() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", 
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 Puppeteer");
        assertTrue(result);
    }

    @Test
    @DisplayName("Detects Selenium user agent")
    void testDetectSelenium() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", 
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 WebDriver");
        assertTrue(result);
    }

    @Test
    @DisplayName("Detects PhantomJS user agent")
    void testDetectPhantomJS() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", 
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.34 (KHTML, like Gecko) PhantomJS/1.9.7");
        assertTrue(result);
    }

    @Test
    @DisplayName("Flags null user agent as malicious")
    void testNullUserAgent() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", null);
        assertTrue(result);
    }

    @Test
    @DisplayName("Flags empty user agent as malicious")
    void testEmptyUserAgent() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", "");
        assertTrue(result);
    }

    @Test
    @DisplayName("Flags blank user agent as malicious")
    void testBlankUserAgent() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", "   ");
        assertTrue(result);
    }

    @Test
    @DisplayName("Detects curl user agent")
    void testDetectCurl() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", "curl/7.68.0");
        assertTrue(result);
    }

    @Test
    @DisplayName("Detects wget user agent")
    void testDetectWget() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", "Wget/1.20.3");
        assertTrue(result);
    }

    @Test
    @DisplayName("Detects Python user agent")
    void testDetectPython() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", "python-requests/2.28.0");
        assertTrue(result);
    }

    @Test
    @DisplayName("Allows legitimate Firefox user agent")
    void testAllowLegitimateUserAgent() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1",
            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0");
        assertFalse(result);
    }

    @Test
    @DisplayName("Allows legitimate Chrome user agent")
    void testAllowChromeUserAgent() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        assertFalse(result);
    }

    @Test
    @DisplayName("Allows legitimate Safari user agent")
    void testAllowSafariUserAgent() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15");
        assertFalse(result);
    }

    @Test
    @DisplayName("Allows normal request velocity")
    void testAllowNormalVelocity() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");
        assertFalse(result);
    }

    @Test
    @DisplayName("Detects velocity anomaly exceeding threshold")
    void testDetectVelocityAnomaly() {
        for (int i = 0; i < 51; i++) {
            if (i < 50) {
                velocityCache.recordRequest("192.168.1.1");
            } else {
                boolean result = threatAnalyzer.isMalicious("192.168.1.1", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");
                assertTrue(result);
                return;
            }
        }
    }

    @Test
    @DisplayName("Handles null IP gracefully")
    void testNullIP() {
        boolean result = threatAnalyzer.isMalicious(null, "Mozilla/5.0 Chrome/120.0");
        assertFalse(result);
    }

    @Test
    @DisplayName("Handles blank IP gracefully")
    void testBlankIP() {
        boolean result = threatAnalyzer.isMalicious("   ", "Mozilla/5.0 Chrome/120.0");
        assertFalse(result);
    }

    @Test
    @DisplayName("Retrieves current velocity for IP")
    void testGetCurrentVelocity() {
        velocityCache.recordRequest("192.168.1.1");
        velocityCache.recordRequest("192.168.1.1");
        velocityCache.recordRequest("192.168.1.1");
        
        int velocity = threatAnalyzer.getCurrentVelocity("192.168.1.1");
        assertEquals(3, velocity);
    }

    @Test
    @DisplayName("Resets the velocity cache")
    void testResetCache() {
        velocityCache.recordRequest("192.168.1.1");
        threatAnalyzer.resetCache();
        
        assertEquals(0, threatAnalyzer.getCurrentVelocity("192.168.1.1"));
    }

    @Test
    @DisplayName("Identifies headless bot combined checks")
    void testCombinedCheckHeadlessBot() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1", "HeadlessChrome/120.0");
        assertTrue(result);
    }

    @Test
    @DisplayName("Passes both checks for legitimate requests")
    void testBothChecksMustPass() {
        boolean result = threatAnalyzer.isMalicious("192.168.1.1",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");
        assertFalse(result);
    }
}
