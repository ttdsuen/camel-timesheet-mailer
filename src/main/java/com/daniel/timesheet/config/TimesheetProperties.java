package com.daniel.timesheet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunable settings for the timesheet job, bound from the {@code timesheet.*} prefix.
 */
@ConfigurationProperties(prefix = "timesheet")
public class TimesheetProperties {

    /** Length of the reporting window in days. 14 = a biweekly period. */
    private int periodDays = 14;

    /** Contracted hours for each working day. */
    private int hoursPerDay = 7;

    /** ISO 3166-2 subdivision code used for regional holidays (e.g. {@code on} for Ontario). */
    private String province = "on";

    /** Where the generated workbook is anchored, and where it is re-read from if edited. */
    private String templatePath = "data/timesheet-template.xlsx";

    /** Directory that keeps a copy of every workbook the job produces (audit trail). */
    private String outboxDir = "data/outbox";

    /** Directory that receives exchanges whose delivery failed after all retries. */
    private String deadLetterDir = "data/deadletter";

    /** Cron expression driving the schedule. Default: 09:00 on the 1st and 3rd Friday of each month. */
    private String schedule = "0 0 9 ? * FRI#1,FRI#3 *";

    /** Email recipient. */
    private String recipient = "me@example.com";

    /** Envelope sender. */
    private String sender = "me@example.com";

    /** Mail subject line. */
    private String subject = "Biweekly timesheet";

    public int getPeriodDays() {
        return periodDays;
    }

    public void setPeriodDays(int periodDays) {
        this.periodDays = periodDays;
    }

    public int getHoursPerDay() {
        return hoursPerDay;
    }

    public void setHoursPerDay(int hoursPerDay) {
        this.hoursPerDay = hoursPerDay;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getOutboxDir() {
        return outboxDir;
    }

    public void setOutboxDir(String outboxDir) {
        this.outboxDir = outboxDir;
    }

    public String getDeadLetterDir() {
        return deadLetterDir;
    }

    public void setDeadLetterDir(String deadLetterDir) {
        this.deadLetterDir = deadLetterDir;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
