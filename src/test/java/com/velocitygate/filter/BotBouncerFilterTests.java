package com.velocitygate.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BotBouncerFilter with lock-free concurrency.
 *
 * @since 1.0.0
 */
@DisplayName("BotBouncerFilter Tests")
class BotBouncerFilterTests {

    private BotBouncerFilter botBouncerFilter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private StringWriter stringWriter;
    private PrintWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        botBouncerFilter = new BotBouncerFilter(1000L, 50, true, true);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);
    }

    @Test
    @DisplayName("Detects HeadlessChrome as malicious")
    void testDetectHeadlessChromeRequest() throws ServletException, IOException {
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 HeadlessChrome/120.0");
        
        botBouncerFilter.doFilter(request, response, filterChain);
        
        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Detects Puppeteer as malicious")
    void testDetectPuppeteerRequest() throws ServletException, IOException {
        when(request.getRemoteAddr()).thenReturn("192.168.1.2");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Puppeteer/1.0");
        
        botBouncerFilter.doFilter(request, response, filterChain);
        
        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Allows legitimate Chrome request")
    void testAllowLegitimateChrome() throws ServletException, IOException {
        when(request.getRemoteAddr()).thenReturn("192.168.1.3");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0");
        
        botBouncerFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).setStatus(403);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Allows legitimate Firefox request")
    void testAllowLegitimateFirefox() throws ServletException, IOException {
        when(request.getRemoteAddr()).thenReturn("192.168.1.4");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0");
        
        botBouncerFilter.doFilter(request, response, filterChain);
        
        verify(response, never()).setStatus(403);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Creates filter instance")
    void testFilterCreation() {
        assertNotNull(botBouncerFilter);
    }

    @Test
    @DisplayName("Detects velocity anomaly with lock-free concurrency")
    void testDetectVelocityAnomaly() throws ServletException, IOException {
        String ip = "192.168.1.5";
        when(request.getRemoteAddr()).thenReturn(ip);
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");
        
        // Make 51 requests (exceeds threshold of 50)
        for (int i = 0; i < 51; i++) {
            botBouncerFilter.doFilter(request, response, filterChain);
        }
        
        // The 51st request should be blocked
        verify(response, atLeastOnce()).setStatus(403);
    }

    @Test
    @DisplayName("Handles X-Forwarded-For header correctly")
    void testXForwardedForHeader() throws ServletException, IOException {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 192.168.1.1");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 Chrome/120.0");
        
        botBouncerFilter.doFilter(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
    }
}
