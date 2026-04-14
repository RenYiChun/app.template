package com.lrenyi.template.flow.autoconfigure;

import java.util.Collections;
import java.util.Map;
import com.lrenyi.template.flow.FlowAutoConfiguration;
import com.lrenyi.template.flow.health.FlowActuatorHealthIndicator;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.health.FlowHealthIndicator;
import com.lrenyi.template.flow.health.HealthStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowAutoConfigurationTest {

    private static final String FLOW_HEALTH_COMPONENT = "flowActuatorHealthIndicator";
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    PropertyPlaceholderAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    SimpleMetricsExportAutoConfiguration.class,
                    EndpointAutoConfiguration.class,
                    HealthContributorAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class,
                    FlowAutoConfiguration.class
            ));

    @AfterEach
    void tearDown() {
        FlowHealth.clearIndicators();
    }

    @Test
    void defaultConfigurationRegistersSingleFlowHealthBridge() {
        contextRunner.run(context -> {
            assertEquals(1, context.getBeansOfType(FlowAutoConfiguration.class).size());
            assertEquals(1, context.getBeansOfType(FlowActuatorHealthIndicator.class).size());
            assertEquals(1, context.getBeansOfType(FlowActuatorHealthIndicator.class).size());
        });
    }

    @Test
    void appTemplateEnabledFalseDisablesFlowAutoConfigurationAndHealthBridge() {
        contextRunner.withPropertyValues("app.template.enabled=false").run(context -> {
            assertTrue(context.getBeansOfType(FlowAutoConfiguration.class).isEmpty());
            assertTrue(context.getBeansOfType(FlowActuatorHealthIndicator.class).isEmpty());
        });
    }

    @Test
    void healthEndpointExposesFlowBridgeStatusAndDetails() {
        FlowHealth.registerIndicator(fixedIndicator("registry", HealthStatus.DEGRADED));

        contextRunner.run(context -> {
            FlowActuatorHealthIndicator indicator = context.getBean(FlowActuatorHealthIndicator.class);
            Health flowHealth = indicator.health();

            assertEquals(new Status(HealthStatus.DEGRADED.name()), flowHealth.getStatus());
            assertEquals(HealthStatus.DEGRADED.name(), flowHealth.getDetails().get("overallStatus"));
            assertNotNull(flowHealth.getDetails().get("indicators"));
        });
    }

    private static FlowHealthIndicator fixedIndicator(String name, HealthStatus status) {
        return new FlowHealthIndicator() {
            @Override
            public HealthStatus checkHealth() {
                return status;
            }

            @Override
            public Map<String, Object> getDetails() {
                return Collections.singletonMap("status", status.name());
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }
}
