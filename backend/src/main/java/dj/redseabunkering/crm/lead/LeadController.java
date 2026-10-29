package dj.redseabunkering.crm.lead;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.lead.LeadDtos.ConversionResult;
import dj.redseabunkering.crm.lead.LeadDtos.LeadDto;
import dj.redseabunkering.crm.lead.LeadDtos.LeadRequest;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeadDto> list(@RequestParam(required = false) LeadStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public LeadDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@Valid @RequestBody LeadRequest req) {
        return Map.of("id", service.create(req));
    }

    @PostMapping("/{id}/convert")
    public ConversionResult convert(@PathVariable UUID id, CurrentUser me) {
        return service.convert(id, me);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id, CurrentUser me) {
        service.reject(id, me);
    }
}
