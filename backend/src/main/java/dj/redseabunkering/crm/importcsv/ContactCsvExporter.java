package dj.redseabunkering.crm.importcsv;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.contact.ContactDtos.ContactRow;

/**
 * Export CSV des contacts au format Excel français : séparateur « ; », UTF-8 avec BOM.
 * Les en-têtes reprennent ceux de l'import, pour qu'un export soit réimportable.
 */
@Component
public class ContactCsvExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("Africa/Djibouti"));

    private final JdbcTemplate jdbc;

    public ContactCsvExporter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] export(List<ContactRow> rows) {
        Map<UUID, String> owners = jdbc.query("select id, first_name || ' ' || last_name from users",
                (rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2)))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var out = new StringBuilder("﻿");
        out.append("prenom;nom;email;telephone;poste;entreprise;source;cycle_de_vie;score;proprietaire;cree_le\r\n");
        for (ContactRow r : rows) {
            out.append(String.join(";",
                    cell(r.firstName()), cell(r.lastName()), cell(r.email()), cell(r.phone()), cell(r.title()),
                    cell(r.companyName()), cell(r.source()), cell(r.lifecycle().label()),
                    String.valueOf(r.score()), cell(owners.get(r.ownerId())), DATE.format(r.createdAt())))
                    .append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Échappement CSV + neutralisation des formules (=, +, -, @) contre l'injection dans Excel. */
    static String cell(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        String s = "=+-@\t\r".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
