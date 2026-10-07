package com.daniel.timesheet.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jxls.transform.poi.JxlsPoiTemplateFillerBuilder;
import org.springframework.stereotype.Service;

/**
 * Renders a filled workbook from the jXLS template.
 *
 * <p>The template defines <em>what</em> the sheet looks like; this class only supplies the
 * data. Values are exposed to the template with the names used in the {@code ${...}}
 * expressions written by {@link TemplateInitializer}.
 */
@Service
public class TimesheetRenderer {

    private final TemplateInitializer templateInitializer;

    public TimesheetRenderer(TemplateInitializer templateInitializer) {
        this.templateInitializer = templateInitializer;
    }

    public byte[] render(TimesheetPeriod period, WorkSummary summary) throws IOException {
        Path template = templateInitializer.resolve();

        Map<String, Object> data = new HashMap<>();
        data.put("periodLabel", period.label());
        data.put("periodStart", period.start());
        data.put("periodEnd", period.end());
        data.put("workingDays", summary.workingDays());
        data.put("hoursPerDay", summary.hoursPerDay());
        data.put("totalHours", summary.totalHours());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JxlsPoiTemplateFillerBuilder.newInstance()
                .withTemplate(template.toFile())
                .build()
                .fill(data, () -> out);
        return out.toByteArray();
    }
}
