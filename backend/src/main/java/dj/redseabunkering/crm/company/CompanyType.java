package dj.redseabunkering.crm.company;

import dj.redseabunkering.crm.common.Labeled;

public enum CompanyType implements Labeled {
    ARMATEUR("Armateur"),
    SHIP_MANAGER("Ship manager"),
    AGENT_MARITIME("Agent maritime"),
    TRADER("Trader"),
    AFFRETEUR("Affréteur");

    private final String label;

    CompanyType(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
