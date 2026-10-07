package com.daniel.timesheet.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TimesheetProperties.class)
public class TimesheetConfiguration {

    /**
     * A {@link Clock} bean so the "today" used to compute the period is injectable and
     * can be fixed in tests.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
