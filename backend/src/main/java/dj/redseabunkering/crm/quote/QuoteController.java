package dj.redseabunkering.crm.quote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.email.EmailService.Draft;
import dj.redseabunkering.crm.quote.QuoteDtos.QuoteEmail;
import dj.redseabunkering.crm.quote.QuoteDtos.QuoteRequest;
import dj.redseabunkering.crm.quote.QuoteDtos.QuoteRow;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService service;
    private final QuoteQueries queries;

    public QuoteController(QuoteService service, QuoteQueries queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    public List<QuoteRow> list(@RequestParam(required = false) UUID dealId) {
        return queries.list(dealId);
    }

    @GetMapping("/{id}")
    public QuoteRow get(@PathVariable UUID id) {
        return service.row(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@Valid @RequestBody QuoteRequest req, CurrentUser me) {
        return Map.of("id", service.create(req, me));
    }

    @GetMapping(path = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        QuoteRow q = service.row(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Devis-" + q.number() + ".pdf").build().toString())
                .body(service.pdf(id));
    }

    @GetMapping("/{id}/email-draft")
    public Draft emailDraft(@PathVariable UUID id) {
        return service.emailDraft(id);
    }

    @PostMapping("/{id}/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@PathVariable UUID id, @Valid @RequestBody QuoteEmail email, CurrentUser me) {
        service.send(id, email, me);
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID id, CurrentUser me) {
        service.accept(id, me);
    }

    @PostMapping("/{id}/refuse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refuse(@PathVariable UUID id, CurrentUser me) {
        service.refuse(id, me);
    }
}
