# WatchTracker – webapp

Prototipo dell'applicazione web descritta nel Problem Statement: backend Java 17+ con Spring Boot,
frontend HTML/CSS/JavaScript, database relazionale (H2 in memoria per lo sviluppo, MySQL con un profilo).

## Requisiti

- **JDK 17 o superiore** (il progetto non compila con Java 8). Se hai più JDK, `JAVA_HOME` deve puntare a uno di questi.
- Nessuna installazione di Maven: il Maven Wrapper (`mvnw`) lo scarica da solo al primo avvio.
- Nessuna chiave da configurare: in `local.properties` c'è già la chiave TMDB comune al gruppo.

## Configurazione: `local.properties`

Il file `webapp/local.properties` è versionato e viene letto all'avvio: i suoi valori sovrascrivono quelli di
`src/main/resources/application.properties`. È l'unico posto da modificare per:

- **la chiave TMDB** del gruppo (`watchtracker.tmdb.api-key`). Se smettesse di funzionare, se ne genera una nuova
  da <https://www.themoviedb.org/settings/api> e si aggiorna quella riga. Jikan (MyAnimeList) non richiede chiavi;
- **il database**: di default H2 in memoria; per MySQL basta commentare il blocco H2 e scommentare quello MySQL,
  già pronto nel file.

Per una prova al volo senza toccare il file si può impostare la variabile d'ambiente `TMDB_API_KEY`.
Il file viene cercato nella cartella da cui si avvia l'applicazione, cioè `webapp/`.

## Avvio

**Windows, via doppio clic:** `webapp/avvia.bat`. Controlla che `JAVA_HOME` punti a un JDK 17+, poi lancia
`mvnw.cmd spring-boot:run`; se qualcosa va storto la finestra resta aperta con il messaggio d'errore.

Da terminale, dentro `webapp/`:

```bash
# Windows (cmd)
mvnw.cmd spring-boot:run
# Windows (PowerShell): serve il prefisso .\
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

Poi aprire <http://localhost:8080>.
La console del database H2 è su <http://localhost:8080/h2> (JDBC URL `jdbc:h2:mem:watchtracker`, utente `sa`, password vuota).

### Con MySQL

Le entità JPA sono identiche; cambia solo la configurazione del datasource. Due modi equivalenti:

- modificare il blocco database in `local.properties` (vedi sopra), oppure
- avviare con il profilo `mysql`, che usa `application-mysql.properties`:

```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

Credenziali di default `root` senza password; il database `watchtracker` viene creato automaticamente.

## Test

```bash
mvnw.cmd test
```

## API REST

| Metodo | Percorso | Requisito | Note |
|---|---|---|---|
| GET | `/api/catalog/search?q=` | RF 0.2, 0.3, 2.1–2.4 | ricerca unificata TMDB + MyAnimeList, anime deduplicati |
| GET | `/api/catalog/{source}/{id}` | RF 0.4, 3.2 | `source` = `TMDB_MOVIE`, `TMDB_TV`, `MAL` |
| POST | `/api/auth/register` | RF 0.1 | `{nickname, email, password}` |
| POST | `/api/auth/login` | RF 1.1 | `{nickname, password}`; sessione via cookie |
| POST | `/api/auth/logout` | RF 1.2 | |
| GET | `/api/auth/me` | RF 1.3 | utente corrente, 401 se non autenticato |
| PATCH | `/api/auth/me/visibility` | RF 1.4 | `{publicProfile: true|false}` |
| GET | `/api/library` | RF 2.8 | |
| POST | `/api/library` | RF 2.5, 2.6 | stato iniziale `DA_VEDERE` |
| PATCH | `/api/library/{id}/status` | RF 2.7 | `{status}` in `DA_VEDERE`, `IN_CORSO`, `COMPLETATO`, `ABBANDONATO` |
| DELETE | `/api/library/{id}` | RF 2.11 | |

## Struttura del codice

```
src/main/java/it/watchtracker/
  catalog/   client TMDB e Jikan, ricerca unificata e deduplicazione
  user/      entità User, registrazione, accesso con sessione HTTP
  library/   entità LibraryEntry e operazioni sulla libreria personale
  common/    eccezioni applicative e gestione degli errori REST
  config/    proprietà dei cataloghi e password encoder
src/main/resources/static/   frontend (index.html, app.js, style.css)
```
