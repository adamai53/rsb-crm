package dj.redseabunkering.crm.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Règle 11 : score d'un contact sur 100. */
class ContactScoringTests {

    @Test
    void hotClientScoresEveryCriterion() {
        var s = ContactScoring.score("Recommandation", Lifecycle.CLIENT, "Bunker buyer", 4, BigDecimal.valueOf(500_000));
        // 20 (source) + 28 (client) + 20 (4 activités) + 18 (> 400 k) + 12 (décideur)
        assertThat(s.total()).isEqualTo(98);
        assertThat(s.parts()).extracting(ContactScoring.Part::points).containsExactly(20, 28, 20, 18, 12);
        assertThat(s.parts().get(0).label()).isEqualTo("Source : Recommandation");
    }

    @Test
    void coldLeadWithUnknownSourceGetsFivePoints() {
        var s = ContactScoring.score("Annuaire", Lifecycle.LEAD, "Comptable", 0, BigDecimal.ZERO);
        assertThat(s.total()).isEqualTo(5);
        assertThat(ContactScoring.score(null, Lifecycle.LEAD, null, 0, null).total()).isEqualTo(5);
    }

    @Test
    void interactionsAreCappedAtTwenty() {
        assertThat(ContactScoring.score("Meta Ads", Lifecycle.LEAD, null, 3, null).parts().get(2).points()).isEqualTo(15);
        assertThat(ContactScoring.score("Meta Ads", Lifecycle.LEAD, null, 12, null).parts().get(2).points()).isEqualTo(20);
    }

    @Test
    void openDealThresholdsAreStrict() {
        assertThat(open("400000.01")).isEqualTo(18);
        assertThat(open("400000")).isEqualTo(12);
        assertThat(open("100000.01")).isEqualTo(12);
        assertThat(open("100000")).isEqualTo(6);
        assertThat(open("0.01")).isEqualTo(6);
        assertThat(open("0")).isZero();
    }

    @Test
    void lifecycleIsSevenPointsPerStep() {
        assertThat(ContactScoring.score("LinkedIn", Lifecycle.SQL, null, 0, null).parts().get(1).points()).isEqualTo(14);
        assertThat(ContactScoring.score("LinkedIn", Lifecycle.OPPORTUNITE, null, 0, null).parts().get(1).points()).isEqualTo(21);
    }

    @Test
    void decisionMakerTitlesAreRecognised() {
        for (String title : new String[] {"Fuel procurement manager", "Responsable achats", "Chef du soutage",
                "Bunker TRADER", "Senior buyer"}) {
            assertThat(ContactScoring.score(null, Lifecycle.LEAD, title, 0, null).parts().get(4).points())
                    .as(title).isEqualTo(12);
        }
        assertThat(ContactScoring.score(null, Lifecycle.LEAD, "Capitaine", 0, null).parts().get(4).points()).isZero();
    }

    private static int open(String amount) {
        return ContactScoring.score(null, Lifecycle.LEAD, null, 0, new BigDecimal(amount)).parts().get(3).points();
    }
}
