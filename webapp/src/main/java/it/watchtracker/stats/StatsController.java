package it.watchtracker.stats;

import it.watchtracker.user.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;
    private final CurrentUser currentUser;

    public StatsController(StatsService stats, CurrentUser currentUser) {
        this.stats = stats;
        this.currentUser = currentUser;
    }

    /** UC-19: dashboard dell'utente autenticato. */
    @GetMapping
    public StatsDto mine(HttpSession session) {
        return stats.compute(currentUser.require(session));
    }
}
