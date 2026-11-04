package com.minibank.statement;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.statement.service.CsvWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * CSV escaping.
 *
 * <p>Every input here is something a customer can type into a payment description, which is why
 * this is tested at all: the export is generated from free text, and a naive join produces either a
 * file with shifted columns or one that executes on open.
 */
@DisplayName("CSV writer")
class CsvWriterTest {

    @Test
    @DisplayName("quotes a field containing a comma")
    void quotesCommas() {
        String csv = new CsvWriter().row(List.of("Coffee, milk and a bun", "4.20")).toCsv();
        assertThat(csv).contains("\"Coffee, milk and a bun\",4.20");
    }

    @Test
    @DisplayName("doubles an embedded quote")
    void doublesQuotes() {
        String csv = new CsvWriter().row(List.of("The \"good\" cafe")).toCsv();
        assertThat(csv).contains("\"The \"\"good\"\" cafe\"");
    }

    @Test
    @DisplayName("quotes a field containing a newline, keeping the row intact")
    void quotesNewlines() {
        String csv = new CsvWriter().row(List.of("line one\nline two", "x")).toCsv();
        assertThat(csv).contains("\"line one\nline two\",x");
        // One record: the embedded newline is inside quotes, so it does not end the row.
        assertThat(csv.split("\r\n")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+SUM(A1)", "-2+3", "@SUM(A1)"})
    @DisplayName("neutralises a field a spreadsheet would treat as a formula")
    void neutralisesFormulas(String dangerous) {
        String csv = new CsvWriter().row(List.of(dangerous)).toCsv();

        // Prefixed with an apostrophe, so the spreadsheet shows the text instead of evaluating it.
        // A payment description is attacker-controlled free text, and the customer's own export is
        // the obvious place for that to land.
        assertThat(csv).contains("'" + dangerous.charAt(0));
    }

    @Test
    @DisplayName("neutralises a formula that also needs quoting")
    void handlesAFormulaWithAComma() {
        String csv = new CsvWriter().row(List.of("=HYPERLINK(\"http://evil\",\"click\")")).toCsv();
        assertThat(csv).startsWith("﻿\"'=HYPERLINK(");
    }

    @Test
    @DisplayName("leaves an ordinary field alone")
    void leavesPlainFieldsAlone() {
        String csv = new CsvWriter().row(List.of("Grocery store", "48.20", "EUR")).toCsv();
        assertThat(csv).isEqualTo("﻿Grocery store,48.20,EUR\r\n");
    }

    @Test
    @DisplayName("starts with a byte order mark so Excel reads UTF-8")
    void startsWithABom() {
        // Without it, Excel on Windows reads the file as the system codepage and every € becomes
        // mojibake.
        assertThat(new CsvWriter().toCsv()).startsWith("﻿");
    }

    @Test
    @DisplayName("writes a null field as empty rather than as the word null")
    void writesNullAsEmpty() {
        List<String> withNull = new java.util.ArrayList<>();
        withNull.add("a");
        withNull.add(null);
        withNull.add("c");
        assertThat(new CsvWriter().row(withNull).toCsv()).isEqualTo("﻿a,,c\r\n");
    }

    @Test
    @DisplayName("ends every record with CRLF")
    void endsRecordsWithCrlf() {
        assertThat(new CsvWriter().row(List.of("a")).row(List.of("b")).toCsv())
                .isEqualTo("﻿a\r\nb\r\n");
    }
}
