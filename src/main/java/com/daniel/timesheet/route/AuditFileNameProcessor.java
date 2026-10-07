package com.daniel.timesheet.route;

import com.daniel.timesheet.service.TimesheetPeriod;
import com.daniel.timesheet.service.WorkSummary;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Sets a safe, period-derived {@link Exchange#FILE_NAME} on the exchange so that the
 * subsequent {@code file:} producer writes an audit copy with a stable name, and the mail
 * step can reuse the same name for the attachment.
 */
public class AuditFileNameProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        TimesheetPeriod period = exchange.getIn().getHeader("period", TimesheetPeriod.class);
        WorkSummary summary = exchange.getIn().getHeader("workSummary", WorkSummary.class);
        if (summary != null) {
            exchange.getIn().setHeader(Exchange.FILE_NAME, summary.fileName());
        } else if (period != null) {
            exchange.getIn().setHeader(Exchange.FILE_NAME,
                    "timesheet-" + period.start() + "-to-" + period.end() + ".xlsx");
        }
    }
}
