package com.daniel.timesheet.service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.daniel.timesheet.config.TimesheetProperties;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TimesheetRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void rendersTemplateWithPeriodAndHours() throws Exception {
        TimesheetProperties props = new TimesheetProperties();
        props.setTemplatePath(tempDir.resolve("template.xlsx").toString());

        TemplateInitializer initializer = new TemplateInitializer(props);
        TimesheetRenderer renderer = new TimesheetRenderer(initializer);

        TimesheetPeriod period = new TimesheetPeriod(
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 5));
        WorkSummary summary = WorkSummary.of(4, 7,
                java.util.List.of(LocalDate.of(2026, 7, 1)), "timesheet-2026-06-29-to-2026-07-05.xlsx");

        byte[] bytes = renderer.render(period, summary);

        assertThat(bytes).isNotEmpty();
        assertThat(Files.exists(Path.of(props.getTemplatePath()))).isTrue();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(cellText(sheet, 2, 1)).isEqualTo("2026-06-29 to 2026-07-05");
            assertThat(cellText(sheet, 3, 1)).isEqualTo("4");
            assertThat(cellText(sheet, 4, 1)).isEqualTo("7");
            assertThat(cellText(sheet, 5, 1)).isEqualTo("28");
        }
    }

    private String cellText(Sheet sheet, int row, int col) {
        // jXLS writes numeric values as numeric cells, so format rather than assume strings.
        return new DataFormatter().formatCellValue(sheet.getRow(row).getCell(col));
    }
}
