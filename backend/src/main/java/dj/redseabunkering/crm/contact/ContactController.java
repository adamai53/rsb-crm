package dj.redseabunkering.crm.contact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.contact.ContactDtos.ContactDetail;
import dj.redseabunkering.crm.contact.ContactDtos.ContactList;
import dj.redseabunkering.crm.contact.ContactDtos.ContactPatch;
import dj.redseabunkering.crm.contact.ContactDtos.ContactRequest;
import dj.redseabunkering.crm.importcsv.ContactCsvExporter;
import dj.redseabunkering.crm.importcsv.ContactImportService;
import dj.redseabunkering.crm.importcsv.ImportReport;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    /** Taille maximale d'un fichier d'import (les fichiers CRM réels font quelques centaines de Ko). */
    private static final long MAX_IMPORT_BYTES = 5L * 1024 * 1024;

    private final ContactService service;
    private final ContactQueries queries;
    private final ContactImportService importer;
    private final ContactCsvExporter exporter;

    public ContactController(ContactService service, ContactQueries queries, ContactImportService importer,
            ContactCsvExporter exporter) {
        this.service = service;
        this.queries = queries;
        this.importer = importer;
        this.exporter = exporter;
    }

    @GetMapping
    public ContactList list(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) Lifecycle lifecycle,
            @RequestParam(defaultValue = "score") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.list(q, ownerId, companyId, lifecycle, sort, "desc".equalsIgnoreCase(dir), page, size);
    }

    @GetMapping("/{id}")
    public ContactDetail get(@PathVariable UUID id, CurrentUser me) {
        return service.detail(id, me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@Valid @RequestBody ContactRequest req, CurrentUser me) {
        return Map.of("id", service.create(req, me));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id, @Valid @RequestBody ContactRequest req, CurrentUser me) {
        service.update(id, req, me);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void patch(@PathVariable UUID id, @RequestBody ContactPatch patch, CurrentUser me) {
        service.patch(id, patch, me);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, CurrentUser me) {
        service.delete(id, me);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport importCsv(@RequestPart("file") MultipartFile file, CurrentUser me) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("Le fichier est vide.");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw ApiException.badRequest("Fichier trop volumineux (5 Mo maximum).");
        }
        return importer.importContacts(file.getBytes(), me);
    }

    @GetMapping(path = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) Lifecycle lifecycle) {
        byte[] csv = exporter.export(queries.export(q, ownerId, lifecycle));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("contacts-rsb.csv").build().toString())
                .body(csv);
    }
}
