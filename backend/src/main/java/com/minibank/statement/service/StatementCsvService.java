package com.minibank.statement.service;

import com.minibank.statement.dto.StatementResponse;
import com.minibank.transfer.dto.TransactionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Renders a statement as a spreadsheet a person can actually open.
 *
 * <p>Amounts are written signed, at the currency's own scale, with a plain decimal point: a
 * spreadsheet parses that as a number in every locale, whereas the four trailing zeros of the
 * storage scale and a currency symbol both turn the column into text.
 */
@Service
public class StatementCsvService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    private static final List<String> HEADER = List.of(
            "Date", "Reference", "Description", "Category", "Counterparty", "Direction", "Amount", "Currency",
            "Status");

    public String render(StatementResponse statement) {
        CsvWriter csv = new CsvWriter();

        // A short preamble, so a file that has been emailed on still says what it is.
        csv.row(List.of("MBank statement"));
        csv.row(List.of("Account", statement.accountNumber()));
        csv.row(List.of("Period", statement.period()));
        csv.row(List.of("Currency", statement.currency().name()));
        csv.row(List.of("Opening balance", plain(statement.openingBalance())));
        csv.row(List.of("Total in", plain(statement.totalIn())));
        csv.row(List.of("Total out", plain(statement.totalOut())));
        csv.row(List.of("Closing balance", plain(statement.closingBalance())));
        csv.row(List.of());
        csv.row(HEADER);

        for (TransactionResponse transaction : statement.transactions()) {
            boolean outgoing = transaction.direction() == com.minibank.ledger.domain.EntryType.DEBIT;
            csv.row(List.of(
                    transaction.completedAt() == null
                            ? TIMESTAMP.format(transaction.createdAt())
                            : TIMESTAMP.format(transaction.completedAt()),
                    nullToEmpty(transaction.reference()),
                    nullToEmpty(transaction.description()),
                    transaction.category() == null ? "" : transaction.category().displayName(),
                    nullToEmpty(transaction.counterpartyAccountNumber()),
                    outgoing ? "OUT" : "IN",
                    // Signed, so a column total in the spreadsheet is the period's net movement
                    // rather than the sum of every amount regardless of direction.
                    (outgoing ? "-" : "") + plain(transaction.amount()),
                    transaction.currency().name(),
                    transaction.status().name()));
        }
        return csv.toCsv();
    }

    /** Two decimals, no exponent, no currency symbol: a number a spreadsheet will treat as one. */
    private static String plain(BigDecimal amount) {
        return amount == null
                ? "0.00"
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
