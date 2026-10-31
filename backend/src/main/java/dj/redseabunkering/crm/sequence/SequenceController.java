package dj.redseabunkering.crm.sequence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.sequence.SequenceService.SequenceDto;

@RestController
@RequestMapping("/api/sequences")
public class SequenceController {

    private final SequenceService service;

    public SequenceController(SequenceService service) {
        this.service = service;
    }

    @GetMapping
    public List<SequenceDto> list() {
        return service.list();
    }

    @PostMapping("/{id}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> enroll(@PathVariable UUID id, @Valid @RequestBody EnrollRequest req, CurrentUser me) {
        return Map.of("id", service.enroll(id, req.contactId(), me));
    }

    @PostMapping("/enrollments/{id}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable UUID id, CurrentUser me) {
        service.stop(id, me);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public void update(@PathVariable UUID id, @RequestBody AutoSendRequest req) {
        service.setAutoSend(id, req.autoSend());
    }

    public record EnrollRequest(@NotNull(message = "Le contact est obligatoire.") UUID contactId) {
    }

    public record AutoSendRequest(boolean autoSend) {
    }
}
