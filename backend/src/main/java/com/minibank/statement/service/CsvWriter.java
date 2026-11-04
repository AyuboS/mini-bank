package com.minibank.statement.service;

import java.util.List;

/**
 * Minimal, correct RFC 4180 CSV.
 *
 * <h2>Two things this gets right that string concatenation does not</h2>
 *
 * <p><b>Quoting.</b> A field containing a comma, a quote or a newline is quoted, and embedded quotes
 * are doubled. A payment description is free text a customer typed, so all three of those are
 * ordinary inputs, and a naive join produces a file whose columns silently shift.
 *
 * <p><b>Formula injection.</b> A field starting with {@code =}, {@code +}, {@code -} or {@code @} is
 * prefixed with a single quote. Spreadsheets treat such a field as a formula, which means a
 * description like {@code =HYPERLINK("http://…")} becomes live content in the recipient's
 * spreadsheet. The customer's own export is the obvious place for that to land, and neutralising it
 * costs one character.
 */
public final class CsvWriter {

    /** Characters that make a spreadsheet treat a field as a formula rather than as text. */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    /**
     * A plain signed decimal.
     *
     * <p>Needed because {@code -} is both a formula starter and the first character of every
     * negative amount in the file. Prefixing {@code -48.20} would turn the Amount column into text
     * and break the one thing a spreadsheet is for — a column total. A numeric literal cannot carry
     * a formula, so it is exempt.
     */
    private static final java.util.regex.Pattern NUMERIC =
            java.util.regex.Pattern.compile("^[+-]?\\d+(\\.\\d+)?$");

    private final StringBuilder out = new StringBuilder();

    /**
     * Starts a document with a UTF-8 byte order mark.
     *
     * <p>Excel on Windows reads a BOM-less UTF-8 file as the system codepage, which turns every €
     * and every non-ASCII name into mojibake. One three-byte marker is the whole fix.
     */
    public CsvWriter() {
        out.append('﻿');
    }

    public CsvWriter row(List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(fields.get(i)));
        }
        // CRLF, as the format specifies. Excel accepts LF; enough other tools do not.
        out.append("\r\n");
        return this;
    }

    public String toCsv() {
        return out.toString();
    }

    private static String escape(String raw) {
        String value = raw == null ? "" : raw;

        if (!value.isEmpty()
                && FORMULA_STARTERS.indexOf(value.charAt(0)) >= 0
                && !NUMERIC.matcher(value).matches()) {
            value = "'" + value;
        }

        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
