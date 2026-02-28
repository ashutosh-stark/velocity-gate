package com.velocitygate.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * High-performance, lock-free servlet filter that intercepts all HTTP requests to detect and block
 * malicious traffic from bots, headless browsers, and automated agents using velocity analysis.
 * <p>
 * This filter implements a zero-latency rate limiting algorithm with lock-free concurrency using
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} for atomic updates.
 * <p>
 * The filter can be disabled via the configuration property {@code velocitygate.enabled}.
 *
 * @since 1.0.0
 */
public class BotBouncerFilter extends OncePerRequestFilter {

    private static final String DENIED_RESPONSE_BODY = "VelocityGate: Access Denied";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String USER_AGENT_HEADER = "User-Agent";

    /**
     * Bot and headless browser signatures for User-Agent detection.
     */
    private static final List<String> BOT_SIGNATURES = List.of(
            "puppeteer",
            "selenium",
            "playwright",
            "headlesschrome",
            "phantomjs",
            "webdriver",
            "chromium",
            "bot",
            "crawler",
            "spider",
            "scraper",
            "curl",
            "wget",
            "httpclient"
    );

    private final ConcurrentHashMap<String, Deque<Long>> requestTimestamps;
    private final long windowDurationMs;
    private final int maxRequestsPerWindow;
    private final boolean enabled;

    /**
     * Constructs a BotBouncerFilter with configurable rate limiting parameters.
     *
     * @param windowDurationMs the time window duration in milliseconds for velocity tracking
     * @param maxRequestsPerWindow the maximum allowed requests per IP within the time window
     * @param enabled whether the filter is enabled or not
     */
    public BotBouncerFilter(long windowDurationMs, int maxRequestsPerWindow, boolean enabled) {
        this.windowDurationMs = windowDurationMs;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.enabled = enabled;
        this.requestTimestamps = new ConcurrentHashMap<>();
    }

    /**
     * Performs threat analysis on each HTTP request and blocks malicious traffic.
     * <p>
     * If the request is identified as malicious, this method responds with HTTP 403 Forbidden
     * and prevents further processing in the filter chain.
     *
     * @param request the HTTP servlet request
     * @param response the HTTP servlet response
     * @param filterChain the filter chain
     * @throws ServletException if an error occurs during filter processing
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader(USER_AGENT_HEADER);

        if (isMalicious(clientIp, userAgent)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write(DENIED_RESPONSE_BODY);
            response.getWriter().flush();
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Determines if a request is malicious based on User-Agent signatures and velocity anomalies.
     *
     * @param ip the client IP address
     * @param userAgent the User-Agent header value
     * @return {@code true} if the request is malicious; {@code false} otherwise
     */
    private boolean isMalicious(String ip, String userAgent) {
        return (userAgent == null || userAgent.isBlank() || BOT_SIGNATURES.stream().anyMatch(sig -> userAgent.toLowerCase().contains(sig))) || isVelocityAnomaly(ip);
    }

    /**
     * Detects velocity anomalies using lock-free atomic operations.
     * <p>
     * This method uses {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}
     * to atomically update the request timestamp deque for the given IP address without explicit locks.
     *
     * @param ip the client IP address
     * @return {@code true} if the IP exceeds the configured request threshold; {@code false} otherwise
     */
    private boolean isVelocityAnomaly(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - windowDurationMs;

        Deque<Long> timestamps = requestTimestamps.compute(ip, (key, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            
            // Add current request timestamp
            deque.addFirst(currentTime);
            
            // Remove timestamps outside the time window
            while (!deque.isEmpty() && deque.peekLast() < windowStart) {
                deque.removeLast();
            }
            
            return deque;
        });

        return timestamps.size() > maxRequestsPerWindow;
    }

    /**
     * Extracts the client IP address from the HTTP request, accounting for proxy configurations.
     * <p>
     * This method checks the X-Forwarded-For header first (used by proxies and load balancers),
     * taking the leftmost IP address if multiple are present. If X-Forwarded-For is not available,
     * the method falls back to the remote address from the request object.
     *
     * @param request the HTTP servlet request
     * @return the resolved client IP address
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Indicates that this filter applies to all requests.
     *
     * @param request the HTTP servlet request
     * @return always {@code false} to indicate this filter should not skip any request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}
