package com.daniel.timesheet.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.daniel.timesheet.config.TimesheetProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingDayCalculatorTest {

    private WorkingDayCalculator calculator(String province, int hoursPerDay) {
        TimesheetProperties props = new TimesheetProperties();
        props.setProvince(province);
        props.setHoursPerDay(hoursPerDay);
        return new WorkingDayCalculator(props);
    }

    @Test
    void excludesWeekendsAndCanadaDay() {
        // 2026-06-29 (Mon) .. 2026-07-05 (Sun); Canada Day falls on Wed 2026-07-01.
        TimesheetPeriod period = new TimesheetPeriod(
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 5));

        WorkSummary summary = calculator("on", 7).summarise(period);

        assertThat(summary.workingDays()).isEqualTo(4);           // Mon, Tue, Thu, Fri
        assertThat(summary.hoursPerDay()).isEqualTo(7);
        assertThat(summary.totalHours()).isEqualByComparingTo(BigDecimal.valueOf(28));
        assertThat(summary.excludedDates()).contains(LocalDate.of(2026, 7, 1));
        assertThat(summary.fileName()).isEqualTo("timesheet-2026-06-29-to-2026-07-05.xlsx");
    }

    @Test
    void excludesChristmasAndBoxingDay() {
        // Week Mon 2026-12-21 .. Sun 2026-12-27. Christmas Day (Fri 25) and Boxing Day (Sat 26).
        TimesheetPeriod period = new TimesheetPeriod(
                LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 27));

        WorkSummary summary = calculator("on", 7).summarise(period);

        assertThat(summary.workingDays()).isEqualTo(4);           // Mon-Thu; Fri is Christmas
        assertThat(summary.excludedDates()).contains(LocalDate.of(2026, 12, 25));
    }

    @Test
    void weekendIsNeverAWorkingDay() {
        WorkingDayCalculator calculator = calculator("on", 7);
        assertThat(calculator.isWorkingDay(LocalDate.of(2026, 7, 4))).isFalse();  // Saturday
        assertThat(calculator.isWorkingDay(LocalDate.of(2026, 7, 6))).isTrue();   // Monday
    }
}
