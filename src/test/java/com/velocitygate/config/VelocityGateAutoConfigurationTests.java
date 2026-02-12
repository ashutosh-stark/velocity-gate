package com.velocitygate.config;

import com.velocitygate.cache.VelocityCache;
import com.velocitygate.filter.BotBouncerFilter;
import com.velocitygate.service.ThreatAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VelocityGateAutoConfiguration.
 *
 * @since 1.0.0
 */
@DisplayName("VelocityGateAutoConfiguration Tests")
class VelocityGateAutoConfigurationTests {

    @Test
    @DisplayName("Auto-configures all beans")
    void testAutoConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(VelocityGateAutoConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(VelocityCache.class));
            assertNotNull(context.getBean(ThreatAnalyzer.class));
            assertNotNull(context.getBean(FilterRegistrationBean.class));
        }
    }

    @Test
    @DisplayName("Creates singleton instances")
    void testSingletonBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(VelocityGateAutoConfiguration.class);
            context.refresh();

            VelocityCache cache1 = context.getBean(VelocityCache.class);
            VelocityCache cache2 = context.getBean(VelocityCache.class);
            
            assertSame(cache1, cache2);
        }
    }

    @Test
    @DisplayName("Wires dependencies correctly")
    void testDependencyWiring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(VelocityGateAutoConfiguration.class);
            context.refresh();

            ThreatAnalyzer analyzer = context.getBean(ThreatAnalyzer.class);
            FilterRegistrationBean<BotBouncerFilter> filterBean = context.getBean(FilterRegistrationBean.class);
            
            assertNotNull(analyzer);
            assertNotNull(filterBean);
        }
    }
}
