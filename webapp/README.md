# WatchTracker – webapp

Prototipo dell'applicazione web descritta nel Problem Statement e nel Requirements Analysis Document
(aree 3.2.1–3.2.5: account, ricerca, libreria e avanzamento, rating e statistiche, profilo pubblico e backup): backend Java 17+ con Spring Boot,
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

Tutte le risposte di errore hanno la forma `{status, message, fields}`: `fields` riporta, per ciascun campo non
valido, il messaggio previsto dalla tabella dei dati del caso d'uso (eccezione comune EC3 del RAD).

| Metodo | Percorso | Requisito / UC | Note |
|---|---|---|---|
| GET | `/api/catalog/search?q=` | RF 0.2, 0.3, 2.1–2.4 / UC-06 | ricerca unificata TMDB + MyAnimeList, anime deduplicati; `q` di 2–100 caratteri |
| GET | `/api/catalog/{source}/{id}` | RF 0.4, 3.2, 3.7 / UC-07 | scheda con stagioni, episodi e stato di prosecuzione; `source` = `TMDB_MOVIE`, `TMDB_TV`, `MAL` |
| POST | `/api/auth/register` | RF 0.1 / UC-01 | `{nickname, email, password, confirmPassword}` |
| POST | `/api/auth/login` | RF 1.1 / UC-02 | `{nickname, password}`; sessione via cookie |
| POST | `/api/auth/logout` | RF 1.2 / UC-02 | |
| GET | `/api/auth/me` | RF 1.3 | utente corrente con impostazioni di visibilità, 401 se non autenticato |
| PATCH | `/api/auth/me/visibility` | RF 1.4, 4.1, 4.4 / UC-04 | `{publicProfile, showLibrary, showStats, showReviews}` |
| GET | `/api/profiles/{nickname}` | RF 0.5, 0.6, 4.2, 4.3 / UC-08 | profilo pubblico in sola lettura, 404 se privato o inesistente |
| GET | `/api/library` | RF 2.8 / UC-11 | elenco con stato, avanzamento, rating (filtri e ordinamento lato client) |
| GET | `/api/library/entry?source=&externalId=` | UC-07 | voce di libreria di un contenuto con gli episodi visti, 404 se assente |
| POST | `/api/library` | RF 2.5, 2.6 / UC-10 | `{source, externalId}`; i dati sono letti dal catalogo; stato iniziale `DA_VEDERE` |
| PATCH | `/api/library/{id}/status` | RF 2.7, 3.1 / UC-12 | `{status}` in `DA_VEDERE`, `IN_CORSO`, `COMPLETATO`, `ABBANDONATO` |
| DELETE | `/api/library/{id}` | RF 2.11 / UC-12 | elimina anche avanzamento, rating e recensione |
| POST | `/api/library/episodes` | RF 3.3–3.6, 3.17, 3.18 / UC-13–16 | `{source, externalId, episodes:[{season, episode}], watched}`; inserisce in libreria se assente; risposta `{entry, added, allWatched, changed}` |
| PUT | `/api/library/{id}/rating` | RF 3.8, 3.9 / UC-17 | `{rating 1–5, review?}`; solo contenuti `COMPLETATO` |
| DELETE | `/api/library/{id}/rating?review=` | UC-17 E4 | rimuove il rating e, se `review=true`, anche la recensione |
| PUT / DELETE | `/api/library/{id}/review` | RF 3.16 / UC-18 | `{review}` |
| GET | `/api/stats` | RF 3.10–3.15 / UC-19 | dashboard personale |
| GET | `/api/backup/export` | RF 4.6, 4.7 / UC-20 | scarica il file JSON di backup (versione del formato e checksum SHA-256) |
| POST | `/api/backup/preview` | RF 4.8, 4.9 / UC-21 | corpo = file di backup; valida e restituisce il riepilogo |
| POST | `/api/backup/import?settings=` | RF 4.10–4.12 / UC-21 | corpo = file di backup; importazione atomica con fusione dei duplicati |

Verso i cataloghi esterni le richieste sono abbandonate dopo 5 secondi e ritentate una sola volta (RNF-R12, RNF-R13).

## Struttura del codice

```
src/main/java/it/watchtracker/
  catalog/   client TMDB e Jikan (stagioni, episodi, prosecuzione), ricerca unificata e deduplicazione
  user/      entità User, registrazione, accesso con sessione HTTP, visibilità del profilo
  library/   entità LibraryEntry ed EpisodeProgress: stati, avanzamento, rating e recensioni
  stats/     calcolo della dashboard personale
  profile/   profilo pubblico in sola lettura
  backup/    esportazione e importazione del backup
  common/    eccezioni applicative e gestione degli errori REST
  config/    proprietà dei cataloghi, timeout HTTP e password encoder
src/main/resources/static/   frontend (index.html, app.js, style.css)
```
