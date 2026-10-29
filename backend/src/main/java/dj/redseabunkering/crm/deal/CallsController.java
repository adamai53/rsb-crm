package dj.redseabunkering.crm.deal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.deal.DealDtos.DealRow;

/** Calendrier des escales : navires attendus à Djibouti (planification des barges, relances avant arrivée). */
@RestController
@RequestMapping("/api/calls")
public class CallsController {

    private final DealQueries queries;
    private final BusinessCalendar calendar;

    public CallsController(DealQueries queries, BusinessCalendar calendar) {
        this.queries = queries;
        this.calendar = calendar;
    }

    @GetMapping("/upcoming")
    public Upcoming upcoming(@RequestParam(defaultValue = "21") int days) {
        int span = Math.max(1, Math.min(days, 90));
        var today = calendar.today();
        return new Upcoming(today.toString(), span, queries.upcomingCalls(today, span));
    }

    /** {@code today} est la date métier (Djibouti) : le frontend s'en sert pour dessiner la frise. */
    public record Upcoming(String today, int days, List<DealRow> calls) {
    }
}
