//@@author naieel04
package finnus.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

//@@author nkyjStar
import finnus.money.Money;

//@@author naieel04
import finnus.transaction.Transaction;
import finnus.transaction.TransactionList;
import finnus.transaction.TransactionType;
import finnus.ui.Ui;

/**
 * Checks the help output and ensures help preserves application state.
 */
class HelpCommandTest {
    @Test
    public void execute_help_printsCompleteUsageMessage() {
        String actualOutput = captureHelpOutput();

        // Keep expected text independent of HelpCommand so output changes are detected.
        String expectedOutput = String.join(System.lineSeparator(),
                "____________________________________________________________",
                "Available commands:",
                //@@author nabijonzzz
                "+--------------+----------------------+----------------------------------------+",
                "| Command      | Description          | Syntax                                 |",
                "+--------------+----------------------+----------------------------------------+",
                "| deposit      | Record income        | deposit AMOUNT [d/DESCRIPTION]         |",
                "|              |                      | [c/CURRENCY] [on/DATE]                 |",
                "| withdraw     | Record spending      | withdraw AMOUNT d/DESCRIPTION          |",
                "|              |                      | [c/CURRENCY] [t/CATEGORY] [on/DATE]    |",
                "| recent       | Recent activity      | recent [NUMBER]                        |",
                "| find         | Search descriptions  | find KEYWORD                           |",
                "| delete       | Delete a record      | delete INDEX                           |",
                "| balance      | Current balance      | balance                                |",
                //@@author chancheese
                "| allowance    | Monthly allowance    | allowance [DAY [a/AMOUNT]              |",
                "|              |                      | [c/CURRENCY]] or off                   |",
                //@@author nabijonzzz
                "| rate         | Manage FX rates      | rate [CURRENCY RATE]                   |",
                "| category     | Manage categories    | category [NAME]                        |",
                "| help         | List commands        | help                                   |",
                "| exit         | Close FinNUS         | exit                                   |",
                "+--------------+----------------------+----------------------------------------+",
                //@@author naieel04
                "",
                "[] means optional.",
                "____________________________________________________________",
                "");
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void execute_help_containsEveryRegisteredCommandOnce() {
        List<String> rows = captureHelpOutput().lines()
                .filter(line -> line.startsWith("|"))
                .skip(1)
                .toList();
        CommandWord[] commands = java.util.Arrays.stream(CommandWord.values())
                .filter(command -> command != CommandWord.OPENING)
                .toArray(CommandWord[]::new);
        //@@author naieel04
        for (CommandWord command : commands) {
            long matchingRows = rows.stream()
                    .filter(row -> row.split("\\|", -1)[1].trim().equals(command.getWord()))
                    .count();
            assertEquals(1, matchingRows, command.getWord() + " must appear in one command row");
        }
    }

    @Test
    public void execute_help_alignsColumnsWithOuterBorders() {
        List<String> tableLines = captureHelpOutput().lines()
                .filter(line -> line.startsWith("|") || line.startsWith("+"))
                .toList();
        String border = tableLines.getFirst();
        List<Integer> expectedBoundaries = findColumnBoundaries(border, '+');
        assertEquals(4, expectedBoundaries.size());
        for (String line : tableLines) {
            assertEquals(80, line.length(), "Table lines must stay within the 80-column limit");
            assertEquals(border.length(), line.length(), "Table rows must fit within the border");
            char delimiter = line.startsWith("+") ? '+' : '|';
            assertEquals(expectedBoundaries, findColumnBoundaries(line, delimiter));
        }
    }

    @Test
    public void wrap_longUnbrokenToken_splitsWithoutLosingCharacters() {
        assertEquals(List.of("abcdefgh", "ijklmnop", "q"), HelpCommand.wrap("abcdefghijklmnopq", 8));
    }

    @Test
    public void wrap_wordsAtColumnWidth_wrapsBetweenWords() {
        assertEquals(List.of("alpha", "beta"), HelpCommand.wrap("alpha beta", 5));
    }

    @Test
    public void wrap_exactWidthToken_staysOnOneLine() {
        assertEquals(List.of("abcdefgh"), HelpCommand.wrap("abcdefgh", 8));
    }

    @Test
    public void wrap_emptyText_returnsNoLines() {
        assertEquals(List.of(), HelpCommand.wrap("", 8));
    }

    @Test
    public void execute_help_preservesExistingTransactions() {
        TransactionList transactions = new TransactionList();
        //@@author nkyjStar
        transactions.add(new Transaction(TransactionType.DEPOSIT, Money.of("100"), "Allowance"));
        transactions.add(new Transaction(TransactionType.WITHDRAWAL, Money.of("4.50"), "Lunch"));
        //@@author naieel04
        List<Transaction> expectedTransactions = transactions.getAll();

        new HelpCommand().execute(transactions, new Ui());

        // Transactions are immutable, so list equality checks their count, order, and contents.
        assertEquals(expectedTransactions, transactions.getAll());
    }

    @Test
    public void isExit_help_returnsFalse() {
        assertFalse(new HelpCommand().isExit());
    }

    @Test
    public void execute_nullUi_throwsAssertionError() {
        assertThrows(AssertionError.class, () ->
                new HelpCommand().execute(new TransactionList(), null));
    }

    /**
     * Captures help output and restores the original stream even when execution fails.
     */
    private String captureHelpOutput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOutput = System.out;
        try (PrintStream capturedOutput = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOutput);
            new HelpCommand().execute(new TransactionList(), new Ui());
        } finally {
            System.setOut(originalOutput);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    /**
     * Finds delimiter positions so alignment is checked independently of the table formatter.
     */
    private List<Integer> findColumnBoundaries(String line, char delimiter) {
        List<Integer> boundaries = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == delimiter) {
                boundaries.add(i);
            }
        }
        return boundaries;
    }
}
//@@author
