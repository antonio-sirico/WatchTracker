package it.watchtracker.user;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.watchtracker.FakeCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** Registrazione, accesso, visibilità del profilo e libreria attraverso le API HTTP (UC-01, UC-02, UC-04). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeCatalog.class)
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void uc01_rejectsInvalidFieldsWithTheMessagesOfTheDataTable() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"a!","email":"non-valida","password":"solotesto","confirmPassword":"solotesto"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.nickname").value(RegisterRequest.NICKNAME_MESSAGE))
                .andExpect(jsonPath("$.fields.email").value(RegisterRequest.EMAIL_MESSAGE))
                .andExpect(jsonPath("$.fields.password", containsString("numero")));

        // UC-01 tabella dei dati: le due password devono coincidere
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"anna","email":"anna@example.com","password":"password1","confirmPassword":"password2"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.confirmPassword").value(RegisterRequest.CONFIRM_MESSAGE));
    }

    @Test
    void registerLoginVisibilityAndLibrary() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/register").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"marco.r","email":"Marco@Example.com","password":"password1","confirmPassword":"password1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("marco.r"))
                .andExpect(jsonPath("$.email").value("marco@example.com"))
                .andExpect(jsonPath("$.publicProfile").value(false))
                .andExpect(jsonPath("$.profilePath").value("/#/u/marco.r"));

        // nickname duplicato, anche con maiuscole diverse (UC-01 E2)
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"MARCO.R","email":"altro@example.com","password":"password1","confirmPassword":"password1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.nickname").value(RegisterRequest.NICKNAME_TAKEN));

        // la libreria richiede l'accesso (RF 0.7)
        mvc.perform(get("/api/library")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/backup/export")).andExpect(status().isUnauthorized());

        // UC-10: i dati del contenuto vengono letti dal catalogo
        mvc.perform(post("/api/library").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DA_VEDERE"))
                .andExpect(jsonPath("$.title").value("One Piece"))
                .andExpect(jsonPath("$.episodeCount").value(4))
                .andExpect(jsonPath("$.genres[0]").value("Action"));

        // stesso contenuto una seconda volta (UC-10 E1)
        mvc.perform(post("/api/library").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Questo contenuto è già nella tua libreria"));

        mvc.perform(get("/api/library").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("One Piece"))
                .andExpect(jsonPath("$[0].watchedEpisodes").value(0));

        // UC-04: profilo pubblico senza sezioni -> errore; con sezioni -> ok
        mvc.perform(patch("/api/auth/me/visibility").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicProfile":true,"showLibrary":false,"showStats":false,"showReviews":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sections").value(VisibilityRequest.NO_SECTION));
        mvc.perform(patch("/api/auth/me/visibility").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publicProfile":true,"showLibrary":true,"showStats":false,"showReviews":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicProfile").value(true))
                .andExpect(jsonPath("$.showStats").value(false));

        // logout e nuovo accesso (UC-02)
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());

        MockHttpSession second = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(second).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"marco.r","password":"sbagliata1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(UserService.BAD_CREDENTIALS));

        mvc.perform(post("/api/auth/login").session(second).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"marco.r","password":"password1"}
                                """))
                .andExpect(status().isOk());

        // RF 4.5: da un'altra sessione si ritrovano gli stessi dati
        mvc.perform(get("/api/library").session(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("One Piece"));
    }
}
