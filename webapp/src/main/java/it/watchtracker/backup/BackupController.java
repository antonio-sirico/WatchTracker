package it.watchtracker.backup;

import it.watchtracker.user.CurrentUser;
import it.watchtracker.user.User;
import jakarta.servlet.http.HttpSession;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backup ed esportazione dei dati personali. Disponibili solo all'utente autenticato nell'area
 * personale, mai dal profilo pubblico (RF 4.13).
 */
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final BackupService backup;
    private final CurrentUser currentUser;

    public BackupController(BackupService backup, CurrentUser currentUser) {
        this.backup = backup;
        this.currentUser = currentUser;
    }

    /** UC-20: genera e scarica il file di backup. */
    @GetMapping("/export")
    public ResponseEntity<BackupFile> export(HttpSession session) {
        User user = currentUser.require(session);
        BackupFile file = backup.export(user);
        String name = "watchtracker-" + user.getNickname() + "-"
                + FILE_DATE.format(file.exportedAt().atZone(ZoneId.systemDefault())) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(file);
    }

    /** UC-21 passi 3–4: valida il file e restituisce il riepilogo da confermare. */
    @PostMapping(value = "/preview", consumes = MediaType.ALL_VALUE)
    public BackupFile.Summary preview(@RequestBody String body, HttpSession session) {
        User user = currentUser.require(session);
        return backup.summarize(user, backup.parse(body));
    }

    /** UC-21 passi 5–7: importa i dati dopo la conferma dell'utente. */
    @PostMapping(value = "/import", consumes = MediaType.ALL_VALUE)
    public BackupFile.ImportResult importBackup(@RequestBody String body,
                                                @RequestParam(name = "settings", defaultValue = "true") boolean settings,
                                                HttpSession session) {
        User user = currentUser.require(session);
        return backup.importBackup(user, backup.parse(body), settings);
    }
}
