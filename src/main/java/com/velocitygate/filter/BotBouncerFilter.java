package com.velocitygate.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
         * Using a static final Set to avoid allocations per request.
         */
        private static final Set<String> BOT_SIGNATURES = Set.of(
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
    private final boolean trustProxy;
    private final ScheduledExecutorService cleaner;

    /**
     * Constructs a BotBouncerFilter with configurable rate limiting parameters.
     *
     * @param windowDurationMs the time window duration in milliseconds for velocity tracking
     * @param maxRequestsPerWindow the maximum allowed requests per IP within the time window
     * @param enabled whether the filter is enabled or not
     */
    public BotBouncerFilter(long windowDurationMs, int maxRequestsPerWindow, boolean enabled, boolean trustProxy) {
        this.windowDurationMs = windowDurationMs;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.enabled = enabled;
        this.trustProxy = trustProxy;
        this.requestTimestamps = new ConcurrentHashMap<>();
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "velocitygate-cleaner");
            t.setDaemon(true);
            return t;
        });

        long initialDelay = Math.max(1000L, windowDurationMs);
        long period = Math.max(1000L, windowDurationMs);
        this.cleaner.scheduleWithFixedDelay(() -> {
            try {
                long now = System.currentTimeMillis();
                long windowStart = now - this.windowDurationMs;
                requestTimestamps.forEach((ip, deque) -> {
                    requestTimestamps.computeIfPresent(ip, (k, d) -> {
                        while (!d.isEmpty() && d.peekLast() < windowStart) {
                            d.removeLast();
                        }
                        return d.isEmpty() ? null : d;
                    });
                });
            } catch (Throwable t) {
                // Keep cleaner alive on unexpected errors
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);
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
        boolean signatureMatch = false;
        if (userAgent == null || userAgent.isBlank()) {
            signatureMatch = true;
        } else {
            String ua = userAgent.toLowerCase(Locale.ROOT);
            for (String sig : BOT_SIGNATURES) {
                if (ua.contains(sig)) {
                    signatureMatch = true;
                    break;
                }
            }
        }
        return signatureMatch || isVelocityAnomaly(ip);
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

        AtomicBoolean exceeded = new AtomicBoolean(false);
        requestTimestamps.compute(ip, (key, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }

            // Add current request timestamp
            deque.addFirst(currentTime);

            // Remove timestamps outside the time window
            while (!deque.isEmpty() && deque.peekLast() < windowStart) {
                deque.removeLast();
            }

            if (deque.size() > maxRequestsPerWindow) {
                exceeded.set(true);
            }

            return deque;
        });

        return exceeded.get();
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
        if (this.trustProxy) {
            String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] ips = xForwardedFor.split(",");
                if (ips.length > 0) {
                    return ips[0].trim();
                }
            }
        }
        return request.getRemoteAddr();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (cleaner != null && !cleaner.isShutdown()) {
                cleaner.shutdownNow();
            }
        } finally {
            super.finalize();
        }
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
