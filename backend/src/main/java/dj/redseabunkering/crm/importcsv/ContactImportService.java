package dj.redseabunkering.crm.importcsv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.company.CompanyRepository;
import dj.redseabunkering.crm.company.CompanyService;
import dj.redseabunkering.crm.contact.Contact;
import dj.redseabunkering.crm.contact.ContactRepository;
import dj.redseabunkering.crm.contact.Lifecycle;
import dj.redseabunkering.crm.importcsv.ImportReport.Issue;

/**
 * Import de contacts depuis un CSV (colonnes prenom, nom, email, telephone, poste, entreprise, source).
 * Les entreprises inconnues sont créées ; un e-mail déjà présent (en base ou plus haut dans le fichier) est ignoré.
 * Les contacts importés appartiennent à l'utilisateur qui importe, au stade Lead.
 */
@Service
public class ContactImportService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_ROWS = 5000;
    private static final int MAX_REPORTED_ISSUES = 100;

    /** Colonnes acceptées (normalisées) → champ ; les en-têtes anglais sont tolérés. */
    private static final Map<String, String> COLUMNS = Map.ofEntries(
            Map.entry("prenom", "first"), Map.entry("firstname", "first"),
            Map.entry("nom", "last"), Map.entry("lastname", "last"), Map.entry("nomdefamille", "last"),
            Map.entry("email", "email"), Map.entry("mail", "email"), Map.entry("courriel", "email"),
            Map.entry("telephone", "phone"), Map.entry("tel", "phone"), Map.entry("phone", "phone"),
            Map.entry("poste", "title"), Map.entry("fonction", "title"), Map.entry("title", "title"),
            Map.entry("entreprise", "company"), Map.entry("societe", "company"), Map.entry("company", "company"),
            Map.entry("source", "source"));

    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final CompanyService companyService;

    public ContactImportService(ContactRepository contacts, CompanyRepository companies,
            CompanyService companyService) {
        this.contacts = contacts;
        this.companies = companies;
        this.companyService = companyService;
    }

    @Transactional
    public ImportReport importContacts(byte[] file, CurrentUser me) {
        List<CsvParser.Row> rows = CsvParser.parse(CsvParser.decode(file));
        if (rows.size() < 2) {
            throw ApiException.badRequest("Le fichier ne contient aucune ligne de données.");
        }
        Map<String, Integer> index = headerIndex(rows.get(0).cells());
        if (!index.containsKey("first") && !index.containsKey("last")) {
            throw ApiException.badRequest("Colonnes « prenom » et « nom » introuvables. "
                    + "En-têtes attendus : prenom, nom, email, telephone, poste, entreprise, source.");
        }
        List<CsvParser.Row> data = rows.subList(1, rows.size());
        if (data.size() > MAX_ROWS) {
            throw ApiException.badRequest("Fichier trop long : " + MAX_ROWS + " lignes maximum par import.");
        }

        List<Issue> issues = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Map<String, UUID> companyIds = new HashMap<>();
        int created = 0;
        int companiesCreated = 0;

        for (CsvParser.Row row : data) {
            String first = cell(row, index, "first", 80);
            String last = cell(row, index, "last", 80);
            if (first.isEmpty() && last.isEmpty()) {
                issue(issues, row.line(), "Prénom et nom manquants : ligne ignorée.");
                continue;
            }
            String email = cell(row, index, "email", 254).toLowerCase(Locale.ROOT);
            if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
                issue(issues, row.line(), "E-mail invalide (« " + email + " ») : ligne ignorée.");
                continue;
            }
            if (!email.isEmpty() && (!seenEmails.add(email) || contacts.existsByEmailIgnoreCase(email))) {
                issue(issues, row.line(), "Contact déjà existant (" + email + ") : ligne ignorée.");
                continue;
            }

            UUID companyId = null;
            String companyName = cell(row, index, "company", 160);
            if (!companyName.isEmpty()) {
                String key = companyName.toLowerCase(Locale.ROOT);
                companyId = companyIds.get(key);
                if (companyId == null) {
                    boolean existed = companies.findByNameIgnoreCase(companyName).isPresent();
                    companyId = companyService.findOrCreate(companyName, me.id()).getId();
                    companyIds.put(key, companyId);
                    if (!existed) {
                        companiesCreated++;
                    }
                }
            }

            var contact = new Contact(first.isEmpty() ? "—" : first, last.isEmpty() ? "—" : last, me.id());
            contact.update(contact.getFirstName(), contact.getLastName(), cell(row, index, "title", 120), email,
                    cell(row, index, "phone", 40), companyId, Lifecycle.LEAD, cell(row, index, "source", 40));
            contacts.save(contact);
            created++;
        }
        return new ImportReport(data.size(), created, data.size() - created, companiesCreated, issues);
    }

    private static Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String field = COLUMNS.get(CsvParser.normalizeHeader(header.get(i)));
            if (field != null) {
                index.putIfAbsent(field, i);
            }
        }
        return index;
    }

    private static String cell(CsvParser.Row row, Map<String, Integer> index, String field, int max) {
        Integer i = index.get(field);
        if (i == null || i >= row.cells().size()) {
            return "";
        }
        String v = row.cells().get(i).trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static void issue(List<Issue> issues, int line, String message) {
        if (issues.size() < MAX_REPORTED_ISSUES) {
            issues.add(new Issue(line, message));
        }
    }
}
