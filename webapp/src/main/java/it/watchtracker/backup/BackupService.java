package it.watchtracker.backup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.watchtracker.catalog.ContentSource;
import it.watchtracker.common.ApiException;
import it.watchtracker.library.EpisodeRef;
import it.watchtracker.library.LibraryEntry;
import it.watchtracker.library.LibraryRepository;
import it.watchtracker.library.WatchStatus;
import it.watchtracker.user.User;
import it.watchtracker.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Esportazione e importazione del backup dei dati personali (RF 4.6–4.13 / UC-20, UC-21). */
@Service
public class BackupService {

    /** Messaggi della tabella dei dati di UC-21. */
    public static final String INVALID = "Il file selezionato non è un backup valido di WatchTracker";
    public static final String INCOMPATIBLE = "Il backup è stato creato con una versione non compatibile";
    public static final String TOO_LARGE = "Il file supera la dimensione massima di 20 MB";
    public static final String FAILED = "Importazione non riuscita: i tuoi dati non sono stati modificati";
    public static final int MAX_BYTES = 20 * 1024 * 1024;

    private final LibraryRepository library;
    private final UserService users;
    private final ObjectMapper mapper;

    public BackupService(LibraryRepository library, UserService users, ObjectMapper mapper) {
        this.library = library;
        this.users = users;
        this.mapper = mapper;
    }

    /** UC-20: raccoglie libreria, avanzamenti, rating, recensioni e impostazioni in un unico file. */
    @Transactional(readOnly = true)
    public BackupFile export(User user) {
        List<BackupFile.Entry> entries = new ArrayList<>();
        for (LibraryEntry e : library.findAllWithProgress(user)) {
            List<BackupFile.WatchedEpisode> watched = e.getWatched().stream()
                    .map(p -> new BackupFile.WatchedEpisode(p.getSeason(), p.getEpisode(), p.getWatchedAt()))
                    .toList();
            entries.add(new BackupFile.Entry(e.getSource(), e.getExternalId(), e.getType(), e.getTitle(),
                    e.getYear(), e.getPosterUrl(), e.genreList(), e.getRuntimeMinutes(), e.getSeasonCount(),
                    e.getEpisodeCount(), e.getContinuation(), e.getNextEpisodeDate(), e.getStatus(),
                    e.getRating(), e.getRatedAt(), e.getReview(), e.getReviewUpdatedAt(),
                    e.getAddedAt(), e.getUpdatedAt(), e.getCompletedAt(), watched));
        }
        BackupFile.Settings settings = new BackupFile.Settings(user.isPublicProfile(), user.isShowLibrary(),
                user.isShowStats(), user.isShowReviews());
        BackupFile.Data data = new BackupFile.Data(settings, entries);
        return new BackupFile(BackupFile.FORMAT, BackupFile.VERSION, Instant.now(), user.getNickname(),
                data, checksum(mapper.valueToTree(data)));
    }

    /** RF 4.8 / UC-21 passo 3: il file deve essere un backup di WatchTracker, compatibile e integro. */
    public BackupFile parse(String body) {
        if (body == null || body.isBlank()) {
            throw invalid(INVALID);
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE, Map.of("file", TOO_LARGE));
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw invalid(INVALID);
        }
        if (root == null || !root.isObject() || !BackupFile.FORMAT.equals(root.path("format").asText(null))) {
            throw invalid(INVALID);
        }
        if (!root.path("version").canConvertToInt() || root.path("version").asInt() != BackupFile.VERSION) {
            throw invalid(INCOMPATIBLE);
        }
        JsonNode data = root.path("data");
        String expected = root.path("checksum").asText(null);
        if (!data.isObject() || expected == null || !expected.equalsIgnoreCase(checksum(data))) {
            throw invalid(INVALID);
        }
        BackupFile file;
        try {
            file = mapper.treeToValue(root, BackupFile.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw invalid(INVALID);
        }
        if (file.data() == null || file.data().entries() == null) {
            throw invalid(INVALID);
        }
        for (BackupFile.Entry entry : file.data().entries()) {
            if (entry.source() == null || entry.externalId() == null || entry.type() == null
                    || entry.title() == null || entry.status() == null) {
                throw invalid(INVALID);
            }
        }
        return file;
    }

    /** RF 4.9 / UC-21 passo 4: riepilogo dei dati contenuti nel file e di quanti sono già presenti. */
    @Transactional(readOnly = true)
    public BackupFile.Summary summarize(User user, BackupFile file) {
        Map<String, LibraryEntry> existing = existingByKey(user);
        int watched = 0;
        int ratings = 0;
        int reviews = 0;
        int present = 0;
        for (BackupFile.Entry e : file.data().entries()) {
            watched += e.watchedOrEmpty().size();
            if (e.rating() != null) {
                ratings++;
            }
            if (e.review() != null && !e.review().isBlank()) {
                reviews++;
            }
            if (existing.containsKey(key(e.source(), e.externalId()))) {
                present++;
            }
        }
        return new BackupFile.Summary(file.exportedBy(), file.exportedAt(), file.data().entries().size(),
                watched, ratings, reviews, present, file.data().entries().isEmpty());
    }

    /**
     * RF 4.10, RF 4.12, RNF-R6 / UC-21 passo 6: integra i dati senza eliminare quelli esistenti.
     * Per un contenuto già presente mantiene il record più avanzato (stato, episodi visti, data di
     * aggiornamento più recente) e il rating e la recensione più recenti. L'operazione è atomica.
     */
    @Transactional(rollbackFor = Exception.class)
    public BackupFile.ImportResult importBackup(User user, BackupFile file, boolean applySettings) {
        Map<String, LibraryEntry> existing = existingByKey(user);
        int imported = 0;
        int merged = 0;
        int episodes = 0;
        for (BackupFile.Entry b : file.data().entries()) {
            LibraryEntry target = existing.get(key(b.source(), b.externalId()));
            if (target == null) {
                target = new LibraryEntry(user, b.source(), b.externalId(), b.type(), b.title());
                target.applySnapshot(b.title(), b.type(), b.year(), b.posterUrl(), b.genres(), b.runtimeMinutes(),
                        b.seasonCount(), b.episodeCount(), b.continuation(), b.nextEpisodeDate());
                target.setStatus(b.status());
                target.restoreRating(b.rating(), b.ratedAt(), b.review(), b.reviewUpdatedAt());
                target.restoreTimestamps(b.addedAt(), b.updatedAt(), b.completedAt());
                for (BackupFile.WatchedEpisode w : b.watchedOrEmpty()) {
                    if (target.markWatched(new EpisodeRef(w.season(), w.episode()), w.watchedAt())) {
                        episodes++;
                    }
                }
                imported++;
            } else {
                episodes += merge(target, b);
                merged++;
            }
            library.save(target);
        }
        if (applySettings && file.data().settings() != null) {
            BackupFile.Settings s = file.data().settings();
            user.setPublicProfile(s.publicProfile());
            user.setShowLibrary(s.showLibrary());
            user.setShowStats(s.showStats());
            user.setShowReviews(s.showReviews());
            users.save(user);
        }
        return new BackupFile.ImportResult(imported, merged, episodes, applySettings);
    }

    /** RF 4.12: fusione di un contenuto già presente. Restituisce il numero di episodi aggiunti. */
    static int merge(LibraryEntry target, BackupFile.Entry b) {
        int added = 0;
        for (BackupFile.WatchedEpisode w : b.watchedOrEmpty()) {
            if (target.markWatched(new EpisodeRef(w.season(), w.episode()), w.watchedAt())) {
                added++;
            }
        }
        boolean backupNewer = b.updatedAt() != null && b.updatedAt().isAfter(target.getUpdatedAt());
        int rankBackup = rank(b.status());
        int rankTarget = rank(target.getStatus());
        if (rankBackup > rankTarget || (rankBackup == rankTarget && backupNewer)) {
            target.setStatus(b.status());
        }
        if (target.getCompletedAt() == null && b.completedAt() != null) {
            target.setCompletedAt(b.completedAt());
        }
        if (b.rating() != null && (target.getRating() == null || isAfter(b.ratedAt(), target.getRatedAt()))) {
            target.restoreRating(b.rating(), b.ratedAt(), target.getReview(), target.getReviewUpdatedAt());
        }
        if (b.review() != null && !b.review().isBlank()
                && (target.getReview() == null || isAfter(b.reviewUpdatedAt(), target.getReviewUpdatedAt()))) {
            target.restoreRating(target.getRating(), target.getRatedAt(), b.review(), b.reviewUpdatedAt());
        }
        target.touch(b.updatedAt());
        return added;
    }

    /** Ordine di avanzamento degli stati: Da vedere < Abbandonato < In corso < Completato. */
    private static int rank(WatchStatus status) {
        return switch (status) {
            case DA_VEDERE -> 0;
            case ABBANDONATO -> 1;
            case IN_CORSO -> 2;
            case COMPLETATO -> 3;
        };
    }

    private static boolean isAfter(Instant a, Instant b) {
        return a != null && (b == null || a.isAfter(b));
    }

    private Map<String, LibraryEntry> existingByKey(User user) {
        Map<String, LibraryEntry> map = new HashMap<>();
        for (LibraryEntry e : library.findAllWithProgress(user)) {
            map.put(key(e.getSource(), e.getExternalId()), e);
        }
        return map;
    }

    private static String key(ContentSource source, String externalId) {
        return source + ":" + externalId;
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest(message, "file", message);
    }

    /** SHA-256 esadecimale della forma canonica (chiavi ordinate, senza spazi) del nodo JSON. */
    String checksum(JsonNode data) {
        try {
            byte[] canonical = mapper.writeValueAsBytes(canonical(data));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Non è stato possibile creare il backup: riprova più tardi");
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(f -> {
                if (!f.getValue().isNull()) {
                    sorted.put(f.getKey(), canonical(f.getValue()));
                }
            });
            ObjectNode out = mapper.createObjectNode();
            sorted.forEach(out::set);
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            node.forEach(n -> out.add(canonical(n)));
            return out;
        }
        return node;
    }
}
