package com.daniel.timesheet.config;

import com.daniel.timesheet.route.MailAttachmentProcessor;
import com.daniel.timesheet.route.TimesheetRoute;
import com.daniel.timesheet.service.PeriodResolver;
import com.daniel.timesheet.service.WorkingDayCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the route as a plain object.
 *
 * <p>Deliberately, the route does not depend on Spring: the trigger URI is decided here
 * from the active profile, which keeps {@link TimesheetRoute} constructible directly in a
 * context-free test. The collaborating services and processors are Spring beans themselves.
 */
@Configuration
public class TimesheetRouteConfiguration {

    @Bean
    public TimesheetRoute timesheetRoute(Environment environment,
                                         PeriodResolver periodResolver,
                                         WorkingDayCalculator workingDayCalculator,
                                         MailAttachmentProcessor mailAttachmentProcessor) {
        return new TimesheetRoute(triggerUri(environment), periodResolver,
                workingDayCalculator, mailAttachmentProcessor);
    }

    /**
     * Resident (default) waits on the in-app cron scheduler; {@code run-once} fires a
     * single exchange so a Kubernetes CronJob's pod can exit on its own.
     */
    private String triggerUri(Environment environment) {
        if (environment.matchesProfiles("run-once")) {
            return "timer:timesheet?delay={{timesheet.startup-delay}}&repeatCount=1";
        }
        return "cron:timesheet?schedule={{timesheet.schedule}}";
    }
}
