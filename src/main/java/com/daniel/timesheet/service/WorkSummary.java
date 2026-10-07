package com.daniel.timesheet.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The result of counting working days across a {@link TimesheetPeriod}.
 *
 * @param workingDays   number of days that are neither a weekend nor a public holiday
 * @param hoursPerDay   contracted hours per working day
 * @param totalHours    {@code workingDays * hoursPerDay}
 * @param excludedDates dates inside the period that were skipped (weekends and holidays)
 * @param fileName      filename to use for the generated workbook attachment
 */
public record WorkSummary(int workingDays, int hoursPerDay, BigDecimal totalHours,
                          List<LocalDate> excludedDates, String fileName) {

    public WorkSummary {
        excludedDates = List.copyOf(excludedDates);
    }

    public static WorkSummary of(int workingDays, int hoursPerDay, List<LocalDate> excludedDates, String fileName) {
        BigDecimal total = BigDecimal.valueOf(workingDays).multiply(BigDecimal.valueOf(hoursPerDay));
        return new WorkSummary(workingDays, hoursPerDay, total, excludedDates, fileName);
    }
}
