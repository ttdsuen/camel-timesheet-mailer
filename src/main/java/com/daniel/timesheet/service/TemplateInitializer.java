package com.daniel.timesheet.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.daniel.timesheet.config.TimesheetProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Guarantees that a jXLS template exists at {@code timesheet.template-path}.
 *
 * <p>jXLS reads its markup from Excel cell <em>comments</em>, so a template cannot simply be
 * created in a text editor. Rather than require a human to open Excel, this component
 * writes a minimal, valid template using Apache POI the first time the app starts. You can
 * then open that file in Excel/LibreOffice, restyle it, or hand-edit the static cells — those
 * edits are preserved because the file is loaded (not regenerated) on subsequent runs.
 */
@Component
public class TemplateInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateInitializer.class);
    private static final String AREA_MARKUP = "jx:area(lastCell=\"B6\")";

    private final TimesheetProperties properties;

    public TemplateInitializer(TimesheetProperties properties) {
        this.properties = properties;
    }

    /** Returns the template path, creating a default template if one is not present. */
    public synchronized Path resolve() throws IOException {
        Path path = Path.of(properties.getTemplatePath());
        if (Files.notExists(path)) {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            createDefaultTemplate(path);
            LOG.info("Created default jXLS template at {}. Edit it in Excel to change the layout.", path);
        }
        return path;
    }

    private void createDefaultTemplate(Path path) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Timesheet");

            Row header = sheet.createRow(0);
            Cell title = header.createCell(0);
            title.setCellValue("Biweekly Timesheet");
            addComment(workbook, sheet, title, AREA_MARKUP, 0, 1, 0, 3);

            writeLabelValue(sheet, 2, "Period", "${periodLabel}");
            writeLabelValue(sheet, 3, "Working days", "${workingDays}");
            writeLabelValue(sheet, 4, "Hours per day", "${hoursPerDay}");
            writeLabelValue(sheet, 5, "Total hours", "${totalHours}");

            sheet.setColumnWidth(0, 18 * 256);
            sheet.setColumnWidth(1, 28 * 256);

            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }

    private void writeLabelValue(Sheet sheet, int rowIndex, String label, String expression) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(expression);
    }

    private void addComment(Workbook workbook, Sheet sheet, Cell cell, String text,
                            int col1, int col2, int row1, int row2) {
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper helper = workbook.getCreationHelper();
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(col1);
        anchor.setCol2(col2);
        anchor.setRow1(row1);
        anchor.setRow2(row2);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(helper.createRichTextString(text));
        cell.setCellComment(comment);
    }
}
