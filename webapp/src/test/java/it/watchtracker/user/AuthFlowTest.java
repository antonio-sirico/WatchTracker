package it.watchtracker.user;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** Registrazione, accesso e libreria attraverso le API HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rf01_rejectsWeakPasswordAndBadNickname() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"a!","email":"non-valida","password":"solotesto"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.nickname").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password", containsString("numero")));
    }

    @Test
    void registerLoginAndUseLibrary() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/register").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"marco.r","email":"Marco@Example.com","password":"password1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("marco.r"))
                .andExpect(jsonPath("$.email").value("marco@example.com"))
                .andExpect(jsonPath("$.publicProfile").value(false));

        // nickname duplicato, anche con maiuscole diverse
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"MARCO.R","email":"altro@example.com","password":"password1"}
                                """))
                .andExpect(status().isConflict());

        // la libreria richiede l'accesso (RF 0.7)
        mvc.perform(get("/api/library")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/library").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21","type":"ANIME","title":"One Piece","year":1999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DA_VEDERE"));

        // stesso contenuto una seconda volta
        mvc.perform(post("/api/library").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"MAL","externalId":"21","type":"ANIME","title":"One Piece"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/library/1/status").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_CORSO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_CORSO"));

        mvc.perform(get("/api/library").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("One Piece"));

        // logout e nuovo accesso
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());

        MockHttpSession second = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(second).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"marco.r","password":"sbagliata1"}
                                """))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login").session(second).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"marco.r","password":"password1"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("marco.r"));
    }
}
