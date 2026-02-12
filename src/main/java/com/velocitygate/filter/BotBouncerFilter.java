package com.velocitygate.filter;

import com.velocitygate.service.ThreatAnalyzer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * High-priority servlet filter that intercepts all HTTP requests to detect and block malicious
 * traffic from bots, headless browsers, and automated agents.
 * <p>
 * This filter executes with the highest precedence in the servlet filter chain to minimize
 * resource consumption and prevent malicious requests from reaching application logic.
 * <p>
 * The filter can be disabled via the configuration property {@code velocitygate.enabled}.
 *
 * @since 1.0.0
 */
public class BotBouncerFilter extends OncePerRequestFilter {

    private static final String DENIED_RESPONSE_BODY = "VelocityGate: Access Denied";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String USER_AGENT_HEADER = "User-Agent";

    private final ThreatAnalyzer threatAnalyzer;

    @Value("${velocitygate.enabled:true}")
    private boolean enabled;

    /**
     * Constructs a BotBouncerFilter with the specified ThreatAnalyzer dependency.
     *
     * @param threatAnalyzer the threat analyzer instance
     */
    public BotBouncerFilter(ThreatAnalyzer threatAnalyzer) {
        this.threatAnalyzer = threatAnalyzer;
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

        if (threatAnalyzer.isMalicious(clientIp, userAgent)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write(DENIED_RESPONSE_BODY);
            response.getWriter().flush();
            return;
        }

        filterChain.doFilter(request, response);
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
