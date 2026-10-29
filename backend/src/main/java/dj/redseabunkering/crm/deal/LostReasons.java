package dj.redseabunkering.crm.deal;

import java.util.List;

/** Motifs de perte proposés (règle 5) : le motif est obligatoire pour passer une transaction en perdue. */
public final class LostReasons {

    public static final List<String> ALL = List.of(
            "Prix concurrent inférieur",
            "Escale annulée par l'armateur",
            "Produit indisponible",
            "Délai de livraison",
            "Crédit refusé",
            "Autre");

    private LostReasons() {
    }
}
