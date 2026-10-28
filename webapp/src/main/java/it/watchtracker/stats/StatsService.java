package it.watchtracker.stats;

import it.watchtracker.catalog.ContentType;
import it.watchtracker.catalog.Continuation;
import it.watchtracker.library.LibraryEntry;
import it.watchtracker.library.LibraryRepository;
import it.watchtracker.library.WatchStatus;
import it.watchtracker.user.User;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calcola la dashboard personale sui soli dati dell'account (RF 3.10, RNF-R3). */
@Service
public class StatsService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final LibraryRepository repository;

    public StatsService(LibraryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StatsDto compute(User user) {
        return compute(repository.findAllWithProgress(user));
    }

    public StatsDto compute(List<LibraryEntry> entries) {
        long totalMinutes = 0;
        int withoutRuntime = 0;
        int completedCount = 0;
        Map<String, Integer> genreCounts = new TreeMap<>();
        Map<String, Integer> byMonth = new TreeMap<>();
        List<Integer> ratings = new ArrayList<>();
        Map<ContentType, List<Integer>> ratingsByType = new EnumMap<>(ContentType.class);
        List<StatsDto.WaitingSeries> waiting = new ArrayList<>();

        for (LibraryEntry e : entries) {
            boolean completed = e.getStatus() == WatchStatus.COMPLETATO;

            // RF 3.11: durata dei film completati e degli episodi visti
            if (e.isSeries()) {
                if (e.watchedCount() > 0) {
                    if (e.getRuntimeMinutes() == null) {
                        withoutRuntime++;
                    } else {
                        totalMinutes += (long) e.watchedCount() * e.getRuntimeMinutes();
                    }
                }
            } else if (completed) {
                if (e.getRuntimeMinutes() == null) {
                    withoutRuntime++;
                } else {
                    totalMinutes += e.getRuntimeMinutes();
                }
            }

            if (completed) {
                completedCount++;
                // RF 3.12: un titolo con più generi incrementa ciascun genere
                for (String g : e.genreList()) {
                    genreCounts.merge(g, 1, Integer::sum);
                }
                // RF 3.13
                if (e.getCompletedAt() != null) {
                    byMonth.merge(MONTH.format(e.getCompletedAt().atZone(ZoneId.systemDefault())), 1, Integer::sum);
                }
                // RF 3.14
                if (e.getRating() != null) {
                    ratings.add(e.getRating());
                    ratingsByType.computeIfAbsent(e.getType(), t -> new ArrayList<>()).add(e.getRating());
                }
            }

            // RF 3.15
            if (e.isSeries() && e.getStatus() == WatchStatus.IN_CORSO && e.getContinuation() != null
                    && (e.getContinuation() == Continuation.Status.PROSSIMAMENTE
                        || e.getContinuation() == Continuation.Status.DATA)) {
                Continuation c = new Continuation(e.getContinuation(), e.getNextEpisodeDate());
                waiting.add(new StatsDto.WaitingSeries(e.getId(), e.getTitle(), e.getType(), e.getPosterUrl(),
                        c.status(), c.nextDate(), c.label(), e.watchedCount(), e.getEpisodeCount()));
            }
        }

        List<StatsDto.GenreCount> genres = genreCounts.entrySet().stream()
                .map(en -> new StatsDto.GenreCount(en.getKey(), en.getValue()))
                .sorted(Comparator.comparingInt(StatsDto.GenreCount::count).reversed()
                        .thenComparing(StatsDto.GenreCount::genre))
                .toList();
        int max = genres.isEmpty() ? 0 : genres.get(0).count();
        List<String> favorite = genres.stream().filter(g -> g.count() == max).map(StatsDto.GenreCount::genre).toList();

        Map<ContentType, Double> averageByType = new LinkedHashMap<>();
        for (ContentType t : ContentType.values()) {
            averageByType.put(t, average(ratingsByType.getOrDefault(t, List.of())));
        }

        waiting.sort(Comparator.comparing(StatsDto.WaitingSeries::nextDate,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(StatsDto.WaitingSeries::title));

        return new StatsDto(entries.size(), completedCount, totalMinutes, formatMinutes(totalMinutes),
                withoutRuntime, genres, favorite,
                byMonth.entrySet().stream().map(en -> new StatsDto.MonthCount(en.getKey(), en.getValue())).toList(),
                average(ratings), averageByType, waiting);
    }

    /** RF 3.14: media aritmetica arrotondata a una cifra decimale, null se non ci sono rating. */
    static Double average(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        double avg = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        return Math.round(avg * 10) / 10.0;
    }

    /**
     * RF 3.11: «2 giorni, 5 ore e 1 minuto», con il singolare quando il valore è 1 e senza le
     * unità pari a zero.
     */
    static String formatMinutes(long totalMinutes) {
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days == 1 ? "1 giorno" : days + " giorni");
        }
        if (hours > 0) {
            parts.add(hours == 1 ? "1 ora" : hours + " ore");
        }
        if (minutes > 0) {
            parts.add(minutes == 1 ? "1 minuto" : minutes + " minuti");
        }
        if (parts.isEmpty()) {
            return "0 minuti";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " e " + parts.get(parts.size() - 1);
    }
}
