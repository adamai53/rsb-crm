package dj.redseabunkering.crm.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/** « Aujourd'hui » au sens métier : la date à Djibouti, quel que soit le fuseau du serveur. */
@Component
public class BusinessCalendar {

    public static final ZoneId ZONE = ZoneId.of("Africa/Djibouti");

    private final Clock clock;

    public BusinessCalendar(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
