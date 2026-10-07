package com.daniel.timesheet.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.daniel.timesheet.config.TimesheetProperties;
import de.focus_shift.jollyday.core.HolidayCalendar;
import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
import org.springframework.stereotype.Service;

/**
 * Counts the billable working days in a {@link TimesheetPeriod}.
 *
 * <p>A day counts only if it is a weekday <em>and</em> not a public holiday for the
 * configured region. Holiday rules (including Easter-relative days such as Good Friday,
 * "Monday before May 25" for Victoria Day, and observed-date shifting) are delegated to
 * Jollyday. The only rule we own is: a working day is a day that is neither a weekend
 * nor a holiday in the configured province.
 */
@Service
public class WorkingDayCalculator {

    private final TimesheetProperties properties;
    private final HolidayManager holidayManager;
    private final String province;

    public WorkingDayCalculator(TimesheetProperties properties) {
        this.properties = properties;
        this.province = properties.getProvince();
        this.holidayManager = HolidayManager.getInstance(ManagerParameters.create(HolidayCalendar.CANADA));
    }

    public WorkSummary summarise(TimesheetPeriod period) {
        int workingDays = 0;
        List<LocalDate> excluded = new ArrayList<>();

        for (LocalDate date = period.start(); !date.isAfter(period.end()); date = date.plusDays(1)) {
            if (isWorkingDay(date)) {
                workingDays++;
            } else {
                excluded.add(date);
            }
        }
        return WorkSummary.of(workingDays, properties.getHoursPerDay(), excluded, fileName(period));
    }

    /** Stable, period-derived filename, e.g. {@code timesheet-2026-09-21-to-2026-10-04.xlsx}. */
    private String fileName(TimesheetPeriod period) {
        return "timesheet-" + period.start() + "-to-" + period.end() + ".xlsx";
    }

    public boolean isWorkingDay(LocalDate date) {
        return !isWeekend(date) && !isHoliday(date);
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private boolean isHoliday(LocalDate date) {
        if (province == null || province.isBlank()) {
            return holidayManager.isHoliday(date);
        }
        return holidayManager.isHoliday(date, province.toLowerCase());
    }
}
