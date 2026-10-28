package dj.redseabunkering.crm.user;

public enum UserStatus {
    /** Inscrit, e-mail pas encore vérifié. */
    PENDING,
    /** E-mail vérifié, en attente de validation par un administrateur. */
    PENDING_APPROVAL,
    ACTIVE,
    DISABLED
}
