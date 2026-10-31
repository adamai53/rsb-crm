package dj.redseabunkering.crm.quote;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.common.FrenchFormat;
import dj.redseabunkering.crm.quote.QuoteDtos.QuoteRow;

/** PDF du devis, même contenu que l'aperçu du prototype : en-tête, client, navire, lignes, conditions. */
@Component
public class QuotePdfRenderer {

    private static final Color RED = new Color(0xD8, 0x14, 0x1C);
    private static final Color INK = new Color(0x16, 0x24, 0x2D);
    private static final Color MUTED = new Color(0x5B, 0x6A, 0x73);
    private static final Color HEAD_BG = new Color(0xEE, 0xF2, 0xF4);
    private static final Color LINE = new Color(0xD9, 0xE0, 0xE4);
    private static final DateTimeFormatter VALID_UNTIL = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH)
            .withZone(BusinessCalendar.ZONE);
    private static final DateTimeFormatter ISSUED = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
            .withZone(BusinessCalendar.ZONE);
    private static final List<String> TERMS = List.of(
            "Prix indicatif, soumis à confirmation de disponibilité au moment de la nomination.",
            "Tolérance de quantité ± 5 % au choix de l'acheteur.",
            "BDN et échantillons MARPOL remis à bord à la fin de la livraison.",
            "Paiement à 30 jours après livraison, sous réserve de l'accord de crédit.");

    public byte[] render(QuoteRow q) {
        var out = new ByteArrayOutputStream();
        var doc = new Document(PageSize.A4, 48, 48, 44, 44);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Devis " + q.number());
        doc.addAuthor("Red Sea Bunkering");
        doc.open();
        doc.add(header(q));
        doc.add(parties(q));
        doc.add(lines(q));
        doc.add(terms());
        doc.close();
        return out.toByteArray();
    }

    private PdfPTable header(QuoteRow q) {
        var table = new PdfPTable(new float[] {3, 2});
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);

        var brand = new PdfPCell();
        brand.setBorder(Rectangle.BOTTOM);
        brand.setBorderColor(RED);
        brand.setBorderWidth(1.5f);
        brand.setPaddingBottom(10);
        brand.addElement(logo());
        brand.addElement(new Paragraph("Fourniture de combustibles marins · Djibouti", font(9, Font.NORMAL, MUTED)));
        table.addCell(brand);

        var meta = new PdfPCell();
        meta.setBorder(Rectangle.BOTTOM);
        meta.setBorderColor(RED);
        meta.setBorderWidth(1.5f);
        meta.setPaddingBottom(10);
        for (String[] line : new String[][] {{"DEVIS " + q.number(), "b"}, {"Émis le " + ISSUED.format(q.createdAt()), ""},
                {"Valable jusqu'au " + VALID_UNTIL.format(q.validUntil()), ""}}) {
            var p = new Paragraph(line[0], font(10, line[1].isEmpty() ? Font.NORMAL : Font.BOLD, INK));
            p.setAlignment(Element.ALIGN_RIGHT);
            meta.addElement(p);
        }
        table.addCell(meta);
        return table;
    }

    private Image logo() {
        try (InputStream in = new ClassPathResource("pdf/logo-rsb.png").getInputStream()) {
            Image image = Image.getInstance(in.readAllBytes());
            image.scaleToFit(130, 60);
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PdfPTable parties(QuoteRow q) {
        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);
        table.addCell(party("Client", or(q.companyName()),
                q.contactName() == null ? "" : "À l'attention de " + q.contactName()));
        table.addCell(party("Navire", q.vesselName() + " · IMO " + or(q.imo()),
                "ETA " + (q.eta() == null ? "à confirmer" : FrenchFormat.longDate(q.eta()))));
        table.addCell(party("Lieu de livraison", or(q.deliveryLocationLabel()), "Livraison par barge"));
        table.addCell(party("Commercial", or(q.ownerName()), ""));
        return table;
    }

    private PdfPCell party(String label, String main, String detail) {
        var cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(10);
        cell.addElement(new Paragraph(label.toUpperCase(Locale.FRENCH), font(8, Font.NORMAL, MUTED)));
        cell.addElement(new Paragraph(main, font(10, Font.BOLD, INK)));
        if (!detail.isEmpty()) {
            cell.addElement(new Paragraph(detail, font(10, Font.NORMAL, INK)));
        }
        return cell;
    }

    private PdfPTable lines(QuoteRow q) {
        var table = new PdfPTable(new float[] {5, 1.6f, 1.6f, 2});
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);
        for (String h : List.of("Désignation", "Quantité", "PU (USD)", "Montant (USD)")) {
            var cell = cell(h, font(9, Font.BOLD, MUTED), !h.equals("Désignation"));
            cell.setBackgroundColor(HEAD_BG);
            table.addCell(cell);
        }
        Font body = font(10, Font.NORMAL, INK);
        table.addCell(cell(q.fuelGradeLabel() + " · conforme ISO 8217", body, false));
        table.addCell(cell(num(q.quantityMt()) + " MT", body, true));
        table.addCell(cell(num(q.priceUsdPerMt()) + "/MT", body, true));
        table.addCell(cell(num(q.productAmountUsd()), body, true));

        table.addCell(cell("Frais de barge et d'accostage", body, false));
        table.addCell(cell("1", body, true));
        table.addCell(cell(num(q.bargeFeeUsd()), body, true));
        table.addCell(cell(q.bargeFeeUsd().signum() == 0 ? "Offerts" : num(q.bargeFeeUsd()), body, true));

        Font total = font(10.5f, Font.BOLD, INK);
        var label = cell("Total hors taxes", total, false);
        label.setColspan(3);
        table.addCell(label);
        table.addCell(cell(num(q.totalUsd()), total, true));
        return table;
    }

    private PdfPCell cell(String text, Font font, boolean right) {
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setPadding(7);
        cell.setHorizontalAlignment(right ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return cell;
    }

    private Paragraph terms() {
        var p = new Paragraph();
        for (String term : TERMS) {
            p.add(new Chunk("•  " + term + "\n", font(9, Font.NORMAL, MUTED)));
        }
        return p;
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, color);
    }

    private static String num(BigDecimal n) {
        return FrenchFormat.number(n);
    }

    private static String or(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
