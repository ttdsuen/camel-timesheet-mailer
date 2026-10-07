package com.daniel.timesheet.config;

import com.daniel.timesheet.route.DeadLetterReportProcessor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.springframework.stereotype.Component;

/**
 * Shared error handling for the timesheet route, declared as a named route configuration
 * so it can be attached by id ({@link #NAME}) and reused by the context-free route test.
 *
 * <p>After three redeliveries with exponential backoff the failure is handled locally: a
 * diagnostic report is written to {@code timesheet.dead-letter-dir} rather than the
 * exchange being lost.
 */
@Component
public class TimesheetErrorConfiguration extends RouteConfigurationBuilder {

    public static final String NAME = "timesheetErrors";

    @Override
    public void configuration() {
        routeConfiguration(NAME)
                .onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(2_000)
                .useExponentialBackOff()
                .backOffMultiplier(2)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(true)
                .process(new DeadLetterReportProcessor())
                .to("file:{{timesheet.dead-letter-dir}}");
    }
}
