package dj.redseabunkering.crm.company;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.common.PageResponse;
import dj.redseabunkering.crm.company.CompanyDtos.CompanyDetail;
import dj.redseabunkering.crm.company.CompanyDtos.CompanyRequest;
import dj.redseabunkering.crm.company.CompanyDtos.CompanyRow;
import dj.redseabunkering.crm.company.CompanyDtos.OwnerRequest;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;
    private final CompanyQueries queries;

    public CompanyController(CompanyService service, CompanyQueries queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<CompanyRow> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(defaultValue = "revenue") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.list(q, ownerId, sort, page, size);
    }

    @GetMapping("/{id}")
    public CompanyDetail get(@PathVariable UUID id, CurrentUser me) {
        return service.detail(id, me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@Valid @RequestBody CompanyRequest req, CurrentUser me) {
        return Map.of("id", service.create(req, me));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id, @Valid @RequestBody CompanyRequest req, CurrentUser me) {
        service.update(id, req, me);
    }

    @PatchMapping("/{id}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwner(@PathVariable UUID id, @Valid @RequestBody OwnerRequest req, CurrentUser me) {
        service.changeOwner(id, req.ownerId(), me);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, CurrentUser me) {
        service.delete(id, me);
    }
}
