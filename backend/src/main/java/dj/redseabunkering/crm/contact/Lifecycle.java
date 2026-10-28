package dj.redseabunkering.crm.contact;

import dj.redseabunkering.crm.common.Labeled;

/** Cycle de vie d'un contact, dans l'ordre de maturité (utilisé par le score). */
public enum Lifecycle implements Labeled {
    LEAD("Lead"),
    MQL("MQL"),
    SQL("SQL"),
    OPPORTUNITE("Opportunité"),
    CLIENT("Client");

    private final String label;

    Lifecycle(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    /** Un contact ne redescend jamais en dessous de ce niveau automatiquement. */
    public Lifecycle atLeast(Lifecycle min) {
        return ordinal() >= min.ordinal() ? this : min;
    }
}
