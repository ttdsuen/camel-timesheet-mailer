package com.daniel.timesheet.service;

import java.time.LocalDate;

/**
 * An inclusive reporting window {@code [start, end]}.
 */
public record TimesheetPeriod(LocalDate start, LocalDate end) {

    public TimesheetPeriod {
        if (start == null || end == null) {
            throw new IllegalArgumentException("period start and end must not be null");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("period start " + start + " is after end " + end);
        }
    }

    /** Human readable label used in the workbook, e.g. {@code 2026-09-21 to 2026-10-04}. */
    public String label() {
        return start + " to " + end;
    }
}
