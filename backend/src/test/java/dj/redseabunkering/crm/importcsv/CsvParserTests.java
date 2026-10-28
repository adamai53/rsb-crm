package dj.redseabunkering.crm.importcsv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CsvParserTests {

    @Test
    void detectsSemicolonAndHandlesQuotes() {
        var rows = CsvParser.parse(String.join("\n",
                "prenom;nom;poste",
                "Hassan;Doualeh;\"Achats; soutage\"",
                "\"Fatouma\";\"Ali \"\"FA\"\"\";\"Ligne 1",
                "Ligne 2\"",
                ";;",
                "Ibrahim;Omar;Trader"));
        assertThat(rows).hasSize(4);
        assertThat(rows.get(1).cells()).containsExactly("Hassan", "Doualeh", "Achats; soutage");
        assertThat(rows.get(2).cells()).containsExactly("Fatouma", "Ali \"FA\"", "Ligne 1\nLigne 2");
        assertThat(rows.get(3).cells()).containsExactly("Ibrahim", "Omar", "Trader");
        assertThat(rows.get(3).line()).isEqualTo(6);
    }

    @Test
    void detectsCommaAndCrlf() {
        var rows = CsvParser.parse("first,last\r\nA,B\r\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).cells()).containsExactly("A", "B");
    }

    @Test
    void decodesUtf8WithBomAndFallsBackToWindows1252() {
        byte[] utf8 = ("﻿Prénom;Société").getBytes(StandardCharsets.UTF_8);
        assertThat(CsvParser.decode(utf8)).isEqualTo("Prénom;Société");
        byte[] excel = "Prénom;Société".getBytes(Charset.forName("windows-1252"));
        assertThat(CsvParser.decode(excel)).isEqualTo("Prénom;Société");
    }

    @Test
    void normalizesHeaders() {
        assertThat(CsvParser.normalizeHeader(" Prénom ")).isEqualTo("prenom");
        assertThat(CsvParser.normalizeHeader("Téléphone")).isEqualTo("telephone");
        assertThat(CsvParser.normalizeHeader("E-mail")).isEqualTo("email");
    }

    @Test
    void exportNeutralizesSpreadsheetFormulas() {
        assertThat(ContactCsvExporter.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(ContactCsvExporter.cell("Achats; soutage")).isEqualTo("\"Achats; soutage\"");
        assertThat(ContactCsvExporter.cell("+253 77 00 00 00")).isEqualTo("'+253 77 00 00 00");
    }
}
