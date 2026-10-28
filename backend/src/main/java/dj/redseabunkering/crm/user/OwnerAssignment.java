package dj.redseabunkering.crm.user;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;

/** Règles d'attribution d'un dossier (contact, entreprise, transaction, tâche). */
@Component
public class OwnerAssignment {

    private final UserRepository users;

    public OwnerAssignment(UserRepository users) {
        this.users = users;
    }

    /** Propriétaire d'un nouveau dossier : celui demandé si l'appelant peut réattribuer, sinon lui-même. */
    public UUID forNewRecord(UUID requested, CurrentUser me) {
        if (requested == null || requested.equals(me.id())) {
            return me.id();
        }
        if (!me.canManageAll()) {
            throw ApiException.forbidden("Seuls les managers et administrateurs peuvent attribuer un dossier à un autre commercial.");
        }
        return requireActive(requested);
    }

    /** Changement de propriétaire d'un dossier existant : réservé aux managers et administrateurs. */
    public UUID reassign(UUID current, UUID requested, CurrentUser me) {
        if (requested == null || Objects.equals(current, requested)) {
            return current;
        }
        if (!me.canManageAll()) {
            throw ApiException.forbidden("Seuls les managers et administrateurs peuvent réattribuer un dossier.");
        }
        return requireActive(requested);
    }

    private UUID requireActive(UUID userId) {
        if (!users.existsByIdAndStatus(userId, UserStatus.ACTIVE)) {
            throw ApiException.badRequest("Le propriétaire choisi n'est pas un utilisateur actif.");
        }
        return userId;
    }
}
