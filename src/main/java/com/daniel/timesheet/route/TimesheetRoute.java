package com.daniel.timesheet.route;

import java.util.concurrent.atomic.AtomicBoolean;

import com.daniel.timesheet.service.PeriodResolver;
import com.daniel.timesheet.service.WorkingDayCalculator;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * The timesheet job expressed as a Camel route:
 *
 * <pre>
 *   trigger → resolve period → count working days → render workbook → audit copy → mail
 * </pre>
 *
 * <p>The consumer that feeds the route is supplied by the caller, which lets the same
 * route body serve two deployment shapes (a resident pod on a {@code cron:} schedule, or
 * a Kubernetes CronJob firing a {@code timer:} once) without the route knowing about
 * either. See {@code TimesheetRouteConfiguration} for how the deployed shape is chosen.
 *
 * <p>Errors go to a dead-letter channel: after three redeliveries with exponential backoff
 * a diagnostic report is written to {@code timesheet.dead-letter-dir}. Re-running for the
 * same period is safe because the workbook is a deterministic function of the period.
 */
public class TimesheetRoute extends RouteBuilder {

    /** Id of the shared error-handling route configuration attached to this route. */
    public static final String ERROR_CONFIGURATION_ID = "timesheetErrors";

    static final String GUARD_PROPERTY = "timesheet.guardAcquired";

    /** Prevents overlapping executions, which matters for the resident shape. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Consumer URI that feeds the route (a cron schedule, or direct: in tests). */
    private final String triggerUri;
    private final PeriodResolver periodResolver;
    private final WorkingDayCalculator workingDayCalculator;
    private final MailAttachmentProcessor mailAttachmentProcessor;

    public TimesheetRoute(String triggerUri,
                          PeriodResolver periodResolver,
                          WorkingDayCalculator workingDayCalculator,
                          MailAttachmentProcessor mailAttachmentProcessor) {
        this.triggerUri = triggerUri;
        this.periodResolver = periodResolver;
        this.workingDayCalculator = workingDayCalculator;
        this.mailAttachmentProcessor = mailAttachmentProcessor;
    }

    @Override
    public void configure() {
        from(triggerUri)
                .routeConfigurationId(ERROR_CONFIGURATION_ID)
                .routeId("timesheet-mailer")
                .process(this::guard)
                .log("Starting timesheet run (period ends ${date:now:yyyy-MM-dd})")
                .bean(periodResolver, "resolve")
                .setHeader("period", body())
                // summarise() binds its TimesheetPeriod argument from the message body.
                .bean(workingDayCalculator, "summarise")
                .setHeader("workSummary", body())
                .log("Computed summary ${body} for period ${header.period}")
                // Render straight from the two domain objects already on the exchange.
                .bean("timesheetRenderer", "render(${header.period}, ${header.workSummary})")
                // Stable, period-derived name for both the audit copy and the attachment.
                .process(new AuditFileNameProcessor())
                // Durable copy so there is a record of exactly what was sent.
                .to("file:{{timesheet.outbox-dir}}?doneFileName=${file:name}.done")
                // Switch the message to body + attachment.
                .process(mailAttachmentProcessor)
                .toD("smtp://{{mail.host}}:{{mail.port}}"
                        + "?from={{timesheet.sender}}"
                        + "&to={{timesheet.recipient}}"
                        + "&subject={{timesheet.subject}}"
                        + "&contentType=text/plain"
                        + "&username={{mail.username}}"
                        + "&password={{mail.password}}"
                        + "&mail.smtp.auth=true"
                        + "&mail.smtp.starttls.enable=true"
                        + "&mail.smtp.starttls.required=true"
                        + "&mail.smtp.connectiontimeout=10000"
                        + "&mail.smtp.timeout=10000")
                .log("Sent timesheet for ${header.period} to {{timesheet.recipient}}")
                .process(this::releaseGuard);
    }

    /**
     * Allows only one in-flight run at a time. A second trigger while the first is still
     * working fails fast (and is recorded in the dead-letter directory) rather than racing.
     */
    private void guard(Exchange exchange) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "A timesheet run is already in progress; skipping the overlapping trigger");
        }
        exchange.setProperty(GUARD_PROPERTY, Boolean.TRUE);
    }

    /** Releases the guard, but only if this exchange is the one that acquired it. */
    private void releaseGuard(Exchange exchange) {
        if (Boolean.TRUE.equals(exchange.getProperty(GUARD_PROPERTY, Boolean.class))) {
            running.set(false);
        }
    }
}
