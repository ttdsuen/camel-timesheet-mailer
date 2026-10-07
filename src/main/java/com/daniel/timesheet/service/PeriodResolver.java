package com.daniel.timesheet.service;

import java.time.Clock;
import java.time.LocalDate;

import com.daniel.timesheet.config.TimesheetProperties;
import org.springframework.stereotype.Service;

/**
 * Computes the reporting period that a run covers.
 *
 * <p>The period ends "today" (per the injected {@link Clock}) and spans
 * {@code timesheet.period-days} days inclusive of both ends. With the default of 14 this
 * yields a rolling biweekly window.
 */
@Service
public class PeriodResolver {

    private final Clock clock;
    private final TimesheetProperties properties;

    public PeriodResolver(Clock clock, TimesheetProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public TimesheetPeriod resolve() {
        return resolveOn(LocalDate.now(clock));
    }

    /** Resolve the period ending on {@code endDate}; exposed for deterministic tests. */
    public TimesheetPeriod resolveOn(LocalDate endDate) {
        int days = properties.getPeriodDays();
        if (days < 1) {
            throw new IllegalStateException("timesheet.period-days must be >= 1 but was " + days);
        }
        LocalDate start = endDate.minusDays(days - 1L);
        return new TimesheetPeriod(start, endDate);
    }
}
