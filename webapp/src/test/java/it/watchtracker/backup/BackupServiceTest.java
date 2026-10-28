package it.watchtracker.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.common.ApiException;
import it.watchtracker.library.EpisodeRef;
import it.watchtracker.library.LibraryEntry;
import it.watchtracker.library.WatchStatus;
import it.watchtracker.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackupServiceTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final BackupService service = new BackupService(null, null, mapper);
    private final User user = new User("anna", "anna@example.com", "hash");

    private BackupFile sample() {
        BackupFile.Entry entry = new BackupFile.Entry(ContentSource.MAL, "21", ContentType.ANIME, "One Piece", 1999,
                null, List.of("Action"), 24, 1, 4, null, null, WatchStatus.IN_CORSO, null, null, null, null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), null,
                List.of(new BackupFile.WatchedEpisode(1, 1, Instant.parse("2026-01-02T00:00:00Z"))));
        BackupFile.Data data = new BackupFile.Data(new BackupFile.Settings(false, true, true, true), List.of(entry));
        return new BackupFile(BackupFile.FORMAT, BackupFile.VERSION, Instant.parse("2026-10-28T10:00:00Z"), "anna",
                data, service.checksum(mapper.valueToTree(data)));
    }

    @Test
    void rf48_validBackupIsParsedAndTamperedOneIsRejected() throws Exception {
        String json = mapper.writeValueAsString(sample());

        BackupFile parsed = service.parse(json);
        assertThat(parsed.data().entries()).hasSize(1);
        assertThat(parsed.data().entries().get(0).watched()).hasSize(1);

        // file alterato: checksum non corrispondente
        ObjectNode tampered = (ObjectNode) mapper.readTree(json);
        ((ObjectNode) tampered.path("data").path("entries").get(0)).put("title", "Altro");
        assertThatThrownBy(() -> service.parse(mapper.writeValueAsString(tampered)))
                .isInstanceOf(ApiException.class).hasMessage(BackupService.INVALID);

        // non è un backup di WatchTracker
        assertThatThrownBy(() -> service.parse("{\"ciao\":1}"))
                .isInstanceOf(ApiException.class).hasMessage(BackupService.INVALID);
        assertThatThrownBy(() -> service.parse("non json"))
                .isInstanceOf(ApiException.class).hasMessage(BackupService.INVALID);

        // versione non compatibile
        ObjectNode future = (ObjectNode) mapper.readTree(json);
        future.put("version", 99);
        assertThatThrownBy(() -> service.parse(mapper.writeValueAsString(future)))
                .isInstanceOf(ApiException.class).hasMessage(BackupService.INCOMPATIBLE);
    }

    @Test
    void rf412_mergeKeepsTheMostAdvancedRecordAndTheMostRecentRating() {
        LibraryEntry target = new LibraryEntry(user, ContentSource.MAL, "21", ContentType.ANIME, "One Piece");
        target.applySnapshot("One Piece", ContentType.ANIME, 1999, null, List.of("Action"), 24, 1, 4, null, null);
        target.markWatched(new EpisodeRef(1, 1), Instant.parse("2026-02-01T00:00:00Z"));
        target.markWatched(new EpisodeRef(1, 2), Instant.parse("2026-02-01T00:00:00Z"));
        target.setStatus(WatchStatus.COMPLETATO);
        target.setRating(3, Instant.parse("2026-02-01T00:00:00Z"));
        target.setReview("Una recensione esistente", Instant.parse("2026-02-01T00:00:00Z"));

        BackupFile.Entry backup = new BackupFile.Entry(ContentSource.MAL, "21", ContentType.ANIME, "One Piece", 1999,
                null, List.of("Action"), 24, 1, 4, null, null, WatchStatus.IN_CORSO,
                5, Instant.parse("2026-03-01T00:00:00Z"), "Recensione più recente", Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"), null,
                List.of(new BackupFile.WatchedEpisode(1, 2, Instant.parse("2026-01-02T00:00:00Z")),
                        new BackupFile.WatchedEpisode(1, 3, Instant.parse("2026-01-03T00:00:00Z"))));

        int added = BackupService.merge(target, backup);

        assertThat(added).isEqualTo(1); // l'episodio 2 c'era già, il 3 è nuovo
        assertThat(target.watchedCount()).isEqualTo(3);
        assertThat(target.getStatus()).isEqualTo(WatchStatus.COMPLETATO); // stato più avanzato conservato
        assertThat(target.getRating()).isEqualTo(5); // rating più recente
        assertThat(target.getReview()).isEqualTo("Recensione più recente");
    }
}
