package dj.redseabunkering.crm.deal;

import java.util.List;
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
import dj.redseabunkering.crm.deal.DealDtos.DealDetail;
import dj.redseabunkering.crm.deal.DealDtos.DealList;
import dj.redseabunkering.crm.deal.DealDtos.DealRequest;
import dj.redseabunkering.crm.deal.DealDtos.OwnerRequest;
import dj.redseabunkering.crm.deal.DealDtos.StageRequest;

@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealService service;
    private final DealQueries queries;

    public DealController(DealService service, DealQueries queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    public DealList list(@RequestParam(required = false) UUID ownerId, @RequestParam(required = false) String q,
            @RequestParam(required = false) String stage) {
        return queries.list(ownerId, q, stage);
    }

    @GetMapping("/lost-reasons")
    public List<String> lostReasons() {
        return LostReasons.ALL;
    }

    @GetMapping("/{id}")
    public DealDetail get(@PathVariable UUID id, CurrentUser me) {
        return service.detail(id, me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@Valid @RequestBody DealRequest req, CurrentUser me) {
        return Map.of("id", service.create(req, me));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id, @Valid @RequestBody DealRequest req, CurrentUser me) {
        service.update(id, req, me);
    }

    @PatchMapping("/{id}/stage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStage(@PathVariable UUID id, @Valid @RequestBody StageRequest req, CurrentUser me) {
        service.changeStage(id, req.stage(), req.lostReason(), me);
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
