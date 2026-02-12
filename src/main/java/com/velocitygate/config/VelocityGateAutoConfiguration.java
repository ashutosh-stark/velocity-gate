package com.velocitygate.config;

import com.velocitygate.cache.VelocityCache;
import com.velocitygate.filter.BotBouncerFilter;
import com.velocitygate.service.ThreatAnalyzer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Boot auto-configuration for VelocityGate.
 * <p>
 * This configuration class initializes all beans required for threat detection and request filtering.
 * It is automatically discovered and applied via the Spring Boot auto-configuration mechanism when
 * the VelocityGate starter library is present on the classpath.
 * <p>
 * Each bean is registered with {@code @ConditionalOnMissingBean} to allow applications to override
 * default implementations if needed.
 *
 * @since 1.0.0
 */
@Configuration
public class VelocityGateAutoConfiguration {

    /**
     * Creates the VelocityCache singleton bean for request velocity tracking.
     *
     * @return a new VelocityCache instance
     */
    @Bean
    @ConditionalOnMissingBean
    public VelocityCache velocityCache() {
        return new VelocityCache();
    }

    /**
     * Creates the ThreatAnalyzer singleton bean for threat detection.
     *
     * @param velocityCache the VelocityCache bean
     * @return a new ThreatAnalyzer instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ThreatAnalyzer threatAnalyzer(VelocityCache velocityCache) {
        return new ThreatAnalyzer(velocityCache);
    }

    /**
     * Creates and registers the BotBouncerFilter with highest precedence.
     * <p>
     * This method wraps the filter in a FilterRegistrationBean to ensure it executes
     * with HIGHEST_PRECEDENCE in the servlet filter chain, maximizing efficiency by
     * intercepting malicious requests before they reach application logic.
     *
     * @param threatAnalyzer the ThreatAnalyzer bean
     * @return a FilterRegistrationBean configured with highest filter precedence
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<BotBouncerFilter> botBouncerFilter(ThreatAnalyzer threatAnalyzer) {
        BotBouncerFilter filter = new BotBouncerFilter(threatAnalyzer);
        FilterRegistrationBean<BotBouncerFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
