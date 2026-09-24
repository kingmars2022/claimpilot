package com.claimpilot.extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and normalizing the values found on policies and receipts. Pure functions, unit-tested. */
public final class Values {

    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.,])(\\d{1,3}(?:[ ,\\u00a0]\\d{3})*|\\d+)(?:[.,](\\d{2}))?(?![\\d])");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b");
    private static final List<DateTimeFormatter> TEXT_DATES = List.of(
            formatter("MMMM d, uuuu", Locale.ENGLISH),
            formatter("MMM d, uuuu", Locale.ENGLISH),
            formatter("d MMMM uuuu", Locale.ENGLISH),
            formatter("d MMM uuuu", Locale.ENGLISH),
            formatter("d MMMM uuuu", Locale.FRENCH),
            formatter("d MMM uuuu", Locale.FRENCH));
    private static final Pattern TEXT_DATE_CANDIDATE = Pattern.compile(
            "(\\p{L}+\\.? \\d{1,2},? \\d{4})|(\\d{1,2}(?:er)? \\p{L}+\\.? \\d{4})");

    private Values() {
    }

    /** Lower case, accents removed, only letters and digits kept: used to compare text loosely. */
    public static String alnum(String text) {
        if (text == null) {
            return "";
        }
        String stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Lower case with runs of whitespace collapsed and typographic quotes and dashes unified. */
    public static String loose(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replace(' ', ' ')
                .replaceAll("\\s+", " ").strip();
    }

    public static Optional<BigDecimal> parseAmount(String text) {
        List<BigDecimal> all = amounts(text);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
    }

    /** Every amount written in the text: "$1,234.50", "85,00 $", "85". */
    public static List<BigDecimal> amounts(String text) {
        List<BigDecimal> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            String whole = m.group(1).replaceAll("[ ,\\u00a0]", "");
            String cents = m.group(2) == null ? "00" : m.group(2);
            result.add(new BigDecimal(whole + "." + cents).setScale(2, RoundingMode.UNNECESSARY));
        }
        return result;
    }

    public static Optional<LocalDate> parseDate(String text) {
        List<LocalDate> all = dates(text);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
    }

    /** Every unambiguous date written in the text: 2026-03-05, March 5, 2026, 5 mars 2026. */
    public static List<LocalDate> dates(String text) {
        List<LocalDate> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) {
            try {
                result.add(LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                        Integer.parseInt(iso.group(3))));
            } catch (RuntimeException ignored) {
                // not a real date
            }
        }
        Matcher words = TEXT_DATE_CANDIDATE.matcher(text);
        while (words.find()) {
            String candidate = words.group().replace("1er ", "1 ").replace(".", "").replace(",", ", ")
                    .replaceAll("\\s+", " ").replace(" ,", ",");
            for (DateTimeFormatter f : TEXT_DATES) {
                try {
                    result.add(LocalDate.parse(candidate, f));
                    break;
                } catch (DateTimeParseException ignored) {
                    // try the next format
                }
            }
        }
        return result;
    }

    /** Normalizes a model-returned value for storage; empty when it cannot be read as its type. */
    public static Optional<String> normalize(FactType type, String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("null")) {
            return Optional.empty();
        }
        String value = raw.strip();
        return switch (type) {
            case DATE -> parseDate(value).map(LocalDate::toString);
            case AMOUNT -> parseAmount(value).map(BigDecimal::toPlainString);
            case TEXT, PHONE -> Optional.of(value.replaceAll("\\s+", " "));
        };
    }

    /** Whether the quote actually contains the value. */
    public static boolean quoteSupports(FactType type, String value, String quote) {
        if (value == null || quote == null) {
            return false;
        }
        return switch (type) {
            case DATE -> dates(quote).stream().anyMatch(d -> d.toString().equals(value));
            case AMOUNT -> amounts(quote).stream().anyMatch(a -> a.toPlainString().equals(value));
            case PHONE -> alnum(quote).contains(alnum(value).replaceFirst("^1(?=\\d{10}$)", ""));
            case TEXT -> alnum(quote).contains(alnum(value));
        };
    }

    private static DateTimeFormatter formatter(String pattern, Locale locale) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(locale);
    }
}
