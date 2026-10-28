package dj.redseabunkering.crm.common;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import dj.redseabunkering.crm.user.Role;

/** Utilisateur de la requête, lu dans l'access token. */
public record CurrentUser(UUID id, Role role) {

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Role role = roles == null || roles.isEmpty() ? Role.SALES : Role.valueOf(roles.get(0));
        return new CurrentUser(UUID.fromString(jwt.getSubject()), role);
    }

    /** ADMIN et MANAGER voient et modifient toutes les données, et peuvent réattribuer les dossiers. */
    public boolean canManageAll() {
        return role == Role.ADMIN || role == Role.MANAGER;
    }

    /** Un SALES ne modifie que les dossiers dont il est propriétaire. */
    public boolean canEdit(UUID ownerId) {
        return canManageAll() || id.equals(ownerId);
    }

    public void requireEdit(UUID ownerId) {
        if (!canEdit(ownerId)) {
            throw ApiException.forbidden("Seul le propriétaire du dossier, un manager ou un administrateur peut le modifier.");
        }
    }
}
