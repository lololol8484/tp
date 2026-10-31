//@@author lololol8484
package finnus.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import finnus.money.Money;

import finnus.transaction.Transaction;
import finnus.transaction.TransactionList;
import finnus.transaction.TransactionType;
import finnus.ui.Ui;

class RecentTransactionCommandTest {
    private final Ui ui = new Ui();

    @Test
    public void execute_emptyList_showsEmptyMessage() {
        TransactionList transactions = new TransactionList();

        String output = runAndCaptureOutput(
                new RecentTransactionCommand(), transactions);

        assertTrue(output.contains(
                "Your list of transactions is currently empty."));
        assertEquals(0, transactions.size());
    }

    @Test
    public void execute_defaultLength_showsMostRecentFiveInReverseOrder() {
        TransactionList transactions = createTransactionList();

        String output = runAndCaptureOutput(
                new RecentTransactionCommand(), transactions);

        assertTrue(output.contains(
                "Here is a list of the recent 5 transaction(s):"));

        assertLinesAppearInOrder(output,
                "1. " + transactions.get(5),
                "2. " + transactions.get(4),
                "3. " + transactions.get(3),
                "4. " + transactions.get(2),
                "5. " + transactions.get(1));

        // The sixth-oldest transaction is outside the default limit.
        assertFalse(output.contains(transactions.get(0).toString()));
    }

    @Test
    public void execute_customLength_showsRequestedNumberOfRecentTransactions() {
        TransactionList transactions = createTransactionList();

        String output = runAndCaptureOutput(
                new RecentTransactionCommand(3), transactions);

        assertTrue(output.contains(
                "Here is a list of the recent 3 transaction(s):"));

        assertLinesAppearInOrder(output,
                "1. " + transactions.get(5),
                "2. " + transactions.get(4),
                "3. " + transactions.get(3));

        // Older transactions beyond the requested limit must not appear.
        assertFalse(output.contains(transactions.get(2).toString()));
        assertFalse(output.contains(transactions.get(1).toString()));
        assertFalse(output.contains(transactions.get(0).toString()));
    }

    @Test
    public void execute_lengthGreaterThanListSize_showsAllTransactions() {
        TransactionList transactions = createSmallTransactionList();

        String output = runAndCaptureOutput(
                new RecentTransactionCommand(5), transactions);

        // The displayed count reflects the actual number of transactions.
        assertTrue(output.contains(
                "Here is a list of the recent 2 transaction(s):"));

        assertLinesAppearInOrder(output,
                "1. " + transactions.get(1),
                "2. " + transactions.get(0));
    }

    @Test
    public void execute_lengthOne_showsOnlyMostRecentTransaction() {
        TransactionList transactions = createTransactionList();

        String output = runAndCaptureOutput(
                new RecentTransactionCommand(1), transactions);

        assertTrue(output.contains(
                "Here is a list of the recent 1 transaction(s):"));
        assertTrue(output.contains("1. " + transactions.get(5)));

        // No other transaction should be displayed.
        for (int i = 0; i < transactions.size() - 1; i++) {
            assertFalse(output.contains(transactions.get(i).toString()));
        }
    }

    @Test
    public void execute_doesNotModifyTransactionList() {
        TransactionList transactions = createTransactionList();
        List<Transaction> originalTransactions = transactions.getAll();
        Money originalBalance = transactions.getBalance();

        runAndCaptureOutput(new RecentTransactionCommand(3), transactions);

        assertEquals(originalTransactions, transactions.getAll());
        assertEquals(originalBalance, transactions.getBalance());
    }

    /**
     * Creates six transactions in chronological order, oldest first.
     */
    private TransactionList createTransactionList() {
        TransactionList transactions = new TransactionList();

        transactions.add(new Transaction(
                TransactionType.DEPOSIT,
                Money.of("100.00"),
                "Allowance"));

        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("2.50"),
                "Transport"));

        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("4.50"),
                "Lunch"));

        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("12.30"),
                "Groceries"));

        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("3.20"),
                "Coffee"));

        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("25.00"),
                "Books"));

        return transactions;
    }

    /**
     * Creates two transactions in chronological order, oldest first.
     */
    private TransactionList createSmallTransactionList() {
        TransactionList transactions = new TransactionList();

        transactions.add(new Transaction(
                TransactionType.DEPOSIT,
                Money.of("100.00"),
                "Allowance"));

        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("4.50"),
                "Lunch"));

        return transactions;
    }

    /**
     * Checks that the expected transaction lines appear in the correct order.
     */
    private void assertLinesAppearInOrder(String output, String... expectedLines) {
        int previousIndex = -1;

        for (String line : expectedLines) {
            int currentIndex = output.indexOf(line);

            assertTrue(currentIndex >= 0,
                    "Expected output to contain: " + line);

            assertTrue(currentIndex > previousIndex,
                    "Expected output line to appear after the previous line: " + line);

            previousIndex = currentIndex;
        }
    }

    /**
     * Runs the command and returns its printed output, restoring System.out afterwards.
     */
    private String runAndCaptureOutput(
            RecentTransactionCommand command, TransactionList transactions) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOutput = System.out;

        try (PrintStream capturedOutput = new PrintStream(
                output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOutput);
            command.execute(transactions, ui);
        } finally {
            System.setOut(originalOutput);
        }

        return output.toString(StandardCharsets.UTF_8);
    }
}
//@@author
