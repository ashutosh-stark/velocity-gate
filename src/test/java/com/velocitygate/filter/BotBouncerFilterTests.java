package com.velocitygate.filter;

import com.velocitygate.service.ThreatAnalyzer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BotBouncerFilter.
 *
 * @since 1.0.0
 */
@DisplayName("BotBouncerFilter Tests")
class BotBouncerFilterTests {

    private BotBouncerFilter botBouncerFilter;
    private ThreatAnalyzer threatAnalyzer;

    @BeforeEach
    void setUp() {
        threatAnalyzer = new ThreatAnalyzer(new com.velocitygate.cache.VelocityCache());
        botBouncerFilter = new BotBouncerFilter(threatAnalyzer);
    }

    @Test
    @DisplayName("Detects HeadlessChrome as malicious")
    void testDetectHeadlessChromeRequest() throws ServletException, IOException {
        String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 HeadlessChrome/120.0";
        boolean isMalicious = threatAnalyzer.isMalicious("192.168.1.1", userAgent);
        assertTrue(isMalicious);
    }

    @Test
    @DisplayName("Detects Puppeteer as malicious")
    void testDetectPuppeteerRequest() throws ServletException, IOException {
        String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Puppeteer/1.0";
        boolean isMalicious = threatAnalyzer.isMalicious("192.168.1.1", userAgent);
        assertTrue(isMalicious);
    }

    @Test
    @DisplayName("Allows legitimate Chrome request")
    void testAllowLegitimateChrome() throws ServletException, IOException {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0";
        boolean isMalicious = threatAnalyzer.isMalicious("192.168.1.1", userAgent);
        assertFalse(isMalicious);
    }

    @Test
    @DisplayName("Allows legitimate Firefox request")
    void testAllowLegitimateFirefox() throws ServletException, IOException {
        String userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0";
        boolean isMalicious = threatAnalyzer.isMalicious("192.168.1.1", userAgent);
        assertFalse(isMalicious);
    }

    @Test
    @DisplayName("Flags null user agent as malicious")
    void testFlagNullUserAgent() {
        boolean isMalicious = threatAnalyzer.isMalicious("192.168.1.1", null);
        assertTrue(isMalicious);
    }

    @Test
    @DisplayName("Flags empty user agent as malicious")
    void testFlagEmptyUserAgent() {
        boolean isMalicious = threatAnalyzer.isMalicious("192.168.1.1", "");
        assertTrue(isMalicious);
    }

    @Test
    @DisplayName("Creates filter instance")
    void testFilterCreation() {
        assertNotNull(botBouncerFilter);
    }

    @Test
    @DisplayName("Detects velocity anomaly")
    void testDetectVelocityAnomaly() {
        String ip = "192.168.1.1";
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0";
        
        for (int i = 0; i < 51; i++) {
            threatAnalyzer.isMalicious(ip, userAgent);
        }
        
        boolean isMalicious = threatAnalyzer.isMalicious(ip, userAgent);
        assertTrue(isMalicious);
    }
}
