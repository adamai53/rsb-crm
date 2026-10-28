package dj.redseabunkering.crm.user;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminService service;
    private final UserMapper mapper;

    public UserAdminController(UserAdminService service, UserMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<UserDto> list() {
        return service.list().stream().map(mapper::toDto).toList();
    }

    @PostMapping("/{id}/approve")
    public UserDto approve(@PathVariable UUID id) {
        return mapper.toDto(service.approve(id));
    }

    @PostMapping("/{id}/disable")
    public UserDto disable(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toDto(service.disable(id, UUID.fromString(jwt.getSubject())));
    }

    @PatchMapping("/{id}/role")
    public UserDto changeRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return mapper.toDto(service.changeRole(id, req.role(), UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/{id}/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@PathVariable UUID id) {
        service.resendVerification(id);
    }

    public record RoleRequest(@NotNull(message = "Le rôle est obligatoire.") Role role) {
    }
}
