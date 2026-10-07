package com.daniel.timesheet.route;

import com.daniel.timesheet.service.WorkSummary;

import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.springframework.stereotype.Component;

/**
 * Turns the rendered workbook into a mail attachment.
 *
 * <p>The upstream step renders the workbook to a {@code byte[]} and is expected to set
 * {@link Exchange#FILE_NAME} to the desired attachment filename. This processor reads that
 * header back and attaches the bytes with the right MIME type. The mail component only
 * starts building a multipart message once the exchange actually has attachments, so this
 * step is what switches the outbound mail from "plain body" to "body + attachment".
 *
 * <p>Requires the {@code camel-attachments} module, which ships with {@code camel-mail}.
 */
@Component
public class MailAttachmentProcessor implements Processor {

    static final String XLSX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override
    public void process(Exchange exchange) throws Exception {
        WorkSummary summary = exchange.getIn().getHeader("workSummary", WorkSummary.class);
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        if (fileName == null) {
            fileName = summary != null ? summary.fileName() : "timesheet.xlsx";
        }

        byte[] workbook = exchange.getIn().getBody(byte[].class);
        if (workbook == null || workbook.length == 0) {
            throw new IllegalStateException("No workbook bytes on the exchange; cannot build the attachment");
        }

        AttachmentMessage message = exchange.getIn(AttachmentMessage.class);
        message.addAttachment(fileName, new DataHandler(new ByteArrayDataSource(workbook, XLSX_MIME_TYPE)));

        // A short, human-readable body sits alongside the attachment.
        exchange.getIn().setBody("Your timesheet for the period covered by this email is attached.\n");
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().setHeader(Exchange.FILE_NAME, fileName);
    }
}
