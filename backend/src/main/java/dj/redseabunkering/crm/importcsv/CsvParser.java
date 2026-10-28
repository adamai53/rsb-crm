package dj.redseabunkering.crm.importcsv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lecture CSV tolérante aux fichiers Excel : séparateur « , » ou « ; » détecté sur l'en-tête,
 * guillemets (y compris retours à la ligne et "" échappés), BOM, et repli en Windows-1252 si le fichier n'est pas en UTF-8.
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /** Découpe le texte en lignes de cellules ; les lignes entièrement vides sont ignorées. */
    public static List<Row> parse(String text) {
        int firstBreak = text.indexOf('\n');
        String header = firstBreak < 0 ? text : text.substring(0, firstBreak);
        char sep = count(header, ';') > count(header, ',') ? ';' : ',';

        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        var cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowLine = 1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (ch == '\n') {
                        line++;
                    }
                    cell.append(ch);
                }
            } else if (ch == '"' && cell.toString().isBlank()) {
                cell.setLength(0);
                quoted = true;
            } else if (ch == sep) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(cell.toString().trim());
                cell.setLength(0);
                addRow(rows, cells, rowLine);
                cells = new ArrayList<>();
                line++;
                rowLine = line;
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString().trim());
        addRow(rows, cells, rowLine);
        return rows;
    }

    private static void addRow(List<Row> rows, List<String> cells, int line) {
        if (cells.stream().anyMatch(c -> !c.isEmpty())) {
            rows.add(new Row(line, List.copyOf(cells)));
        }
    }

    /** Nom de colonne normalisé : minuscules, sans accents ni espaces (« Prénom » → « prenom »). */
    public static String normalizeHeader(String h) {
        String s = Normalizer.normalize(h, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static long count(String s, char c) {
        return s.chars().filter(x -> x == c).count();
    }

    public record Row(int line, List<String> cells) {
    }
}
