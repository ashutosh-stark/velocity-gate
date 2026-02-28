package com.velocitygate.config;

import com.velocitygate.filter.BotBouncerFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VelocityGateAutoConfiguration.
 *
 * @since 1.0.0
 */
@DisplayName("VelocityGateAutoConfiguration Tests")
class VelocityGateAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VelocityGateAutoConfiguration.class);

    @Test
    @DisplayName("Auto-configures BotBouncerFilter bean")
    void testAutoConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            FilterRegistrationBean<?> filterBean = context.getBean(FilterRegistrationBean.class);
            assertThat(filterBean.getFilter()).isInstanceOf(BotBouncerFilter.class);
        });
    }

    @Test
    @DisplayName("Creates singleton filter instance")
    void testSingletonBeans() {
        contextRunner.run(context -> {
            FilterRegistrationBean<?> bean1 = context.getBean(FilterRegistrationBean.class);
            FilterRegistrationBean<?> bean2 = context.getBean(FilterRegistrationBean.class);
            assertThat(bean1).isSameAs(bean2);
        });
    }

    @Test
    @DisplayName("Uses default configuration values")
    void testDefaultConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
        });
    }

    @Test
    @DisplayName("Respects custom configuration values")
    void testCustomConfiguration() {
        contextRunner
                .withPropertyValues(
                        "velocitygate.window.duration.ms=2000",
                        "velocitygate.max.requests.per.window=100"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                });
    }
}
