package com.daniel.timesheet.route;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.daniel.timesheet.service.TimesheetPeriod;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Replaces the failed exchange body with a human-readable report before it is written to
 * the dead-letter directory.
 *
 * <p>Writing the original (possibly binary) payload is not very useful — a half-written
 * {@code .xlsx} is not what an operator needs. A short text report naming the period and
 * the failure is greppable and explains what to re-run.
 */
public class DeadLetterReportProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        TimesheetPeriod period = exchange.getIn().getHeader("period", TimesheetPeriod.class);

        String report = """
                timesheet-mailer dead letter
                time:    %s
                route:   %s
                period:  %s
                error:   %s
                message: %s
                """.formatted(
                Instant.now(),
                exchange.getFromRouteId(),
                period != null ? period.label() : "(period not resolved)",
                cause != null ? cause.getClass().getName() : "(unknown)",
                cause != null ? String.valueOf(cause.getMessage()) : "(no message)");

        exchange.getIn().setBody(report.getBytes(StandardCharsets.UTF_8));
        exchange.getIn().setHeader(Exchange.FILE_NAME,
                "dead-letter-" + Instant.now().toString().replace(':', '-') + ".txt");
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain; charset=UTF-8");
    }
}
