package it.watchtracker.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.watchtracker.FakeCatalog;
import it.watchtracker.backup.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Avanzamento, completamento, rating, dashboard, profilo pubblico e backup attraverso le API HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeCatalog.class)
class LibraryFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private MockHttpSession register(String nickname) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\",\"email\":\"" + nickname
                                + "@example.com\",\"password\":\"password1\",\"confirmPassword\":\"password1\"}"))
                .andExpect(status().isCreated());
        return session;
    }

    @Test
    void episodesCompletionRatingStatsProfileAndBackup() throws Exception {
        MockHttpSession antonio = register("antonio");

        // UC-13 + RF 3.17: contrassegnare un episodio di un contenuto non in libreria lo inserisce e lo porta In corso
        mvc.perform(post("/api/library/episodes").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21","episodes":[{"season":1,"episode":1}],"watched":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(true))
                .andExpect(jsonPath("$.allWatched").value(false))
                .andExpect(jsonPath("$.changed").value(1))
                .andExpect(jsonPath("$.entry.status").value("IN_CORSO"))
                .andExpect(jsonPath("$.entry.watchedEpisodes").value(1))
                .andExpect(jsonPath("$.entry.watched[0].episode").value(1));

        // UC-17 E1: non si può valutare un contenuto non completato
        MvcResult entry = mvc.perform(get("/api/library/entry").session(antonio)
                        .param("source", "MAL").param("externalId", "21"))
                .andExpect(status().isOk()).andReturn();
        long entryId = mapper.readTree(entry.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(put("/api/library/" + entryId + "/rating").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(RatingRequest.NOT_COMPLETED));

        // UC-14 + UC-16: episodi 2–4 in un'unica operazione; tutti visti -> proposta di completamento
        mvc.perform(post("/api/library/episodes").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21","watched":true,
                                 "episodes":[{"season":1,"episode":2},{"season":1,"episode":3},{"season":1,"episode":4}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(false))
                .andExpect(jsonPath("$.changed").value(3))
                .andExpect(jsonPath("$.allWatched").value(true))
                .andExpect(jsonPath("$.entry.status").value("IN_CORSO"));

        // UC-13 E1: annullare un contrassegno
        mvc.perform(post("/api/library/episodes").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21","watched":false,"episodes":[{"season":1,"episode":4}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.watchedEpisodes").value(3));
        mvc.perform(post("/api/library/episodes").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21","watched":true,"episodes":[{"season":1,"episode":4}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allWatched").value(true));

        // UC-16 passo 2: conferma del completamento, poi rating e recensione (UC-17)
        mvc.perform(patch("/api/library/" + entryId + "/status").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETATO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").exists());
        mvc.perform(put("/api/library/" + entryId + "/rating").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"review\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.review").value(RatingRequest.REVIEW_MESSAGE));
        mvc.perform(put("/api/library/" + entryId + "/rating").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"review\":\"Un classico che non invecchia mai.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.review").value("Un classico che non invecchia mai."));

        // un film: Completato registra la visione (RF 3.1) e la durata entra nel tempo totale
        mvc.perform(post("/api/library").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"TMDB_MOVIE\",\"externalId\":\"550\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/library/episodes").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"TMDB_MOVIE","externalId":"550","watched":true,"episodes":[{"season":1,"episode":1}]}
                                """))
                .andExpect(status().isBadRequest());
        MvcResult library = mvc.perform(get("/api/library").session(antonio)).andExpect(status().isOk()).andReturn();
        JsonNode entries = mapper.readTree(library.getResponse().getContentAsString());
        long filmId = -1;
        for (JsonNode n : entries) {
            if (n.path("externalId").asText().equals("550")) {
                filmId = n.path("id").asLong();
            }
        }
        mvc.perform(patch("/api/library/" + filmId + "/status").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETATO\"}"))
                .andExpect(status().isOk());

        // UC-19: dashboard
        mvc.perform(get("/api/stats").session(antonio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.totalMinutes").value(4 * 24 + 139))
                .andExpect(jsonPath("$.watchTimeLabel").value("3 ore e 55 minuti"))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.favoriteGenres[0]").value("Action"))
                .andExpect(jsonPath("$.waiting").isEmpty());

        // UC-08 E1: profilo privato -> non disponibile, anche per il proprietario
        mvc.perform(get("/api/profiles/antonio")).andExpect(status().isNotFound());
        mvc.perform(get("/api/profiles/nessuno")).andExpect(status().isNotFound());

        // UC-04: reso pubblico con libreria e recensioni ma senza statistiche
        mvc.perform(patch("/api/auth/me/visibility").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicProfile\":true,\"showLibrary\":true,\"showStats\":false,\"showReviews\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/profiles/Antonio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("antonio"))
                .andExpect(jsonPath("$.library.length()").value(2))
                .andExpect(jsonPath("$.stats").doesNotExist())
                .andExpect(jsonPath("$.reviews[0].review").value("Un classico che non invecchia mai."));

        // RF 4.4: tornato privato, il link non funziona più
        mvc.perform(patch("/api/auth/me/visibility").session(antonio).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicProfile\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/profiles/antonio")).andExpect(status().isNotFound());

        // UC-20: esportazione
        MvcResult export = mvc.perform(get("/api/backup/export").session(antonio))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.format").value("watchtracker-backup"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.checksum").isString())
                .andReturn();
        String backupJson = export.getResponse().getContentAsString();

        // UC-21 in un altro account (RF 4.11): riepilogo, poi importazione
        MockHttpSession martina = register("martina");
        mvc.perform(post("/api/library").session(martina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"MAL\",\"externalId\":\"21\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/backup/preview").session(martina).contentType(MediaType.APPLICATION_JSON)
                        .content(backupJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").value(2))
                .andExpect(jsonPath("$.watchedEpisodes").value(4))
                .andExpect(jsonPath("$.ratings").value(1))
                .andExpect(jsonPath("$.reviews").value(1))
                .andExpect(jsonPath("$.alreadyPresent").value(1));
        mvc.perform(post("/api/backup/import").session(martina).param("settings", "false")
                        .contentType(MediaType.APPLICATION_JSON).content(backupJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.merged").value(1))
                .andExpect(jsonPath("$.watchedEpisodes").value(4));

        // RF 4.12: il contenuto già presente è stato fuso, senza duplicati, con stato e rating del backup
        MvcResult merged = mvc.perform(get("/api/library/entry").session(martina)
                        .param("source", "MAL").param("externalId", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETATO"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.watchedEpisodes").value(4))
                .andReturn();
        assertThat(merged.getResponse().getContentAsString()).contains("Un classico");
        mvc.perform(get("/api/library").session(martina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // RF 4.8: un file non valido non modifica nulla
        mvc.perform(post("/api/backup/import").session(martina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"altro\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.file").value(BackupService.INVALID));

        // RF 2.11: la rimozione elimina anche avanzamento, rating e recensione
        mvc.perform(delete("/api/library/" + entryId).session(antonio)).andExpect(status().isNoContent());
        mvc.perform(get("/api/library/entry").session(antonio).param("source", "MAL").param("externalId", "21"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/stats").session(antonio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }
}
