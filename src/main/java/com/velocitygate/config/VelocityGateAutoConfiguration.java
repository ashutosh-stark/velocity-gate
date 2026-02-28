package com.velocitygate.config;

import com.velocitygate.filter.BotBouncerFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Boot auto-configuration for VelocityGate.
 * <p>
 * This configuration class initializes the high-performance, lock-free BotBouncerFilter
 * with configurable rate limiting parameters. It is automatically discovered and applied
 * via the Spring Boot auto-configuration mechanism when the VelocityGate starter library
 * is present on the classpath.
 * <p>
 * The bean is registered with {@code @ConditionalOnMissingBean} to allow applications
 * to override the default implementation if needed.
 *
 * @since 1.0.0
 */
@Configuration
public class VelocityGateAutoConfiguration {

    /**
     * Creates and registers the BotBouncerFilter with highest precedence.
     * <p>
     * This method wraps the filter in a FilterRegistrationBean to ensure it executes
     * with HIGHEST_PRECEDENCE in the servlet filter chain, maximizing efficiency by
     * intercepting malicious requests before they reach application logic.
     * <p>
     * The filter uses lock-free concurrency with ConcurrentHashMap for zero-latency
     * rate limiting and bot detection.
     *
     * @param windowDurationMs the time window duration in milliseconds for velocity tracking
     * @param maxRequestsPerWindow the maximum allowed requests per IP within the time window
     * @param enabled whether the filter is enabled or not
     * @return a FilterRegistrationBean configured with highest filter precedence
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<BotBouncerFilter> botBouncerFilter(
            @Value("${velocitygate.window.duration.ms:1000}") long windowDurationMs,
            @Value("${velocitygate.max.requests.per.window:50}") int maxRequestsPerWindow,
            @Value("${velocitygate.enabled:true}") boolean enabled) {
        BotBouncerFilter filter = new BotBouncerFilter(windowDurationMs, maxRequestsPerWindow, enabled);
        FilterRegistrationBean<BotBouncerFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
