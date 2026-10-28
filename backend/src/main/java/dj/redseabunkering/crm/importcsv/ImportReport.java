package dj.redseabunkering.crm.importcsv;

import java.util.List;

/** Rapport d'import renvoyé à l'utilisateur : ce qui a été créé, ignoré, et pourquoi. */
public record ImportReport(int rows, int created, int skipped, int companiesCreated, List<Issue> issues) {

    public record Issue(int line, String message) {
    }
}
