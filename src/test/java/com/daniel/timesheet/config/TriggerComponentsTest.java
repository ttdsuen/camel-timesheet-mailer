package com.daniel.timesheet.config;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the runtime dependency the route test cannot see.
 *
 * <p>{@code TimesheetRouteTest} triggers the route through {@code direct:start}, so it
 * never resolves the real consumer endpoints. When the resident profile started for the
 * first time in a cluster it died with {@code No endpoint could be found for:
 * cron://timesheet} because the {@code cron:} component ({@code camel-cron}) was missing
 * from the classpath. These tests resolve both trigger schemes against the real classpath,
 * so a missing component fails the build instead of the pod.
 */
class TriggerComponentsTest {

    @Test
    void residentCronEndpointIsAvailable() {
        assertEndpointResolves("cron:timesheet?schedule=0+0+9+?+*+FRI%231,FRI%233+*");
    }

    @Test
    void runOnceTimerEndpointIsAvailable() {
        assertEndpointResolves("timer:timesheet?delay=3000&repeatCount=1");
    }

    private void assertEndpointResolves(String uri) {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            assertThatCode(() -> context.getEndpoint(uri)).doesNotThrowAnyException();
        } catch (Exception e) {
            throw new AssertionError("Failed to resolve endpoint " + uri, e);
        }
    }
}
