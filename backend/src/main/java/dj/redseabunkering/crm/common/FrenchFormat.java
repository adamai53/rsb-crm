package dj.redseabunkering.crm.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats français des documents et e-mails (espace insécable classique, compatible avec les polices PDF). */
public final class FrenchFormat {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private FrenchFormat() {
    }

    public static String number(BigDecimal n) {
        var symbols = new DecimalFormatSymbols(Locale.FRENCH);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(n);
    }

    public static String longDate(LocalDate date) {
        return date == null ? "" : LONG_DATE.format(date);
    }
}
