package com.daniel.timesheet.route;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;

import com.daniel.timesheet.config.TimesheetErrorConfiguration;
import com.daniel.timesheet.config.TimesheetProperties;
import com.daniel.timesheet.service.PeriodResolver;
import com.daniel.timesheet.service.TemplateInitializer;
import com.daniel.timesheet.service.TimesheetRenderer;
import com.daniel.timesheet.service.WorkingDayCalculator;

import jakarta.activation.DataHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the whole route without a Spring context: the route and its collaborators are
 * plain objects, the trigger is {@code direct:start}, and the SMTP producer is intercepted
 * and routed to a mock. No container boot, no network.
 */
class TimesheetRouteTest extends CamelTestSupport {

    private static final String FILE_NAME = "timesheet-2026-09-21-to-2026-10-04.xlsx";

    private PeriodResolver periodResolver;
    private WorkingDayCalculator workingDayCalculator;
    private MailAttachmentProcessor mailAttachmentProcessor;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        TimesheetProperties properties = new TimesheetProperties();
        properties.setTemplatePath("target/test-data/template.xlsx");
        properties.setOutboxDir("target/test-data/outbox");
        properties.setDeadLetterDir("target/test-data/deadletter");

        // Pin "today" so the resolved period, and therefore the filename, is deterministic.
        Clock fixed = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);
        periodResolver = new PeriodResolver(fixed, properties);
        workingDayCalculator = new WorkingDayCalculator(properties);
        mailAttachmentProcessor = new MailAttachmentProcessor();

        // The route refers to the renderer by name.
        context.getRegistry().bind("timesheetRenderer",
                new TimesheetRenderer(new TemplateInitializer(properties)));

        return context;
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties properties = new Properties();
        properties.setProperty("timesheet.dead-letter-dir", "target/test-data/deadletter");
        properties.setProperty("timesheet.outbox-dir", "target/test-data/outbox");
        properties.setProperty("timesheet.startup-delay", "0");
        properties.setProperty("timesheet.recipient", "recipient@example.com");
        properties.setProperty("timesheet.sender", "sender@example.com");
        properties.setProperty("timesheet.subject", "Test timesheet");
        properties.setProperty("mail.host", "localhost");
        properties.setProperty("mail.port", "3025");
        properties.setProperty("mail.username", "user");
        properties.setProperty("mail.password", "secret");
        return properties;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        return new RouteBuilder[] {
                new TimesheetRoute("direct:start", periodResolver, workingDayCalculator, mailAttachmentProcessor),
                new TimesheetErrorConfiguration()
        };
    }

    /**
     * CamelTestSupport does not auto-start the context when advice is in play, so the
     * dynamic SMTP producer is swapped for a mock here before starting.
     */
    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @BeforeEach
    void replaceMailEndpoint() throws Exception {
        AdviceWith.adviceWith(context, "timesheet-mailer", route ->
                route.weaveByType(ToDynamicDefinition.class).replace().to("mock:mail"));
        context.start();
    }

    @Test
    void rendersWorkbookAndSendsItAsAnAttachment() throws Exception {
        MockEndpoint mail = getMockEndpoint("mock:mail");
        mail.expectedMessageCount(1);

        template.sendBody("direct:start", "");

        mail.assertIsSatisfied();
        Exchange exchange = mail.getExchanges().get(0);
        Map<String, DataHandler> attachments =
                exchange.getIn(AttachmentMessage.class).getAttachments();

        assertThat(attachments).containsKey(FILE_NAME);
        assertThat(attachments.get(FILE_NAME).getContentType())
                .isEqualTo(MailAttachmentProcessor.XLSX_MIME_TYPE);
    }
}
