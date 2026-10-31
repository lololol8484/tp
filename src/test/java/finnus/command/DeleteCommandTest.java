//@@author lololol8484
package finnus.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import finnus.money.Money;

import finnus.exception.FinNusException;
import finnus.transaction.Transaction;
import finnus.transaction.TransactionList;
import finnus.transaction.TransactionType;
import finnus.ui.Ui;

class DeleteCommandTest {
    private final Ui ui = new Ui();

    @Test
    public void execute_validIndex_deletesSelectedTransaction() throws FinNusException {
        TransactionList transactions = createTransactionList();

        new DeleteCommand(2).execute(transactions, ui);

        assertTransactionDescriptions(transactions, "Allowance", "Lunch");
    }

    @Test
    public void execute_indexOne_deletesMostRecentTransaction() throws FinNusException {
        TransactionList transactions = createTransactionList();

        new DeleteCommand(1).execute(transactions, ui);

        assertTransactionDescriptions(transactions, "Allowance", "Transport");
    }

    @Test
    public void execute_indexEqualToSize_deletesOldestTransaction() throws FinNusException {
        TransactionList transactions = createTransactionList();

        new DeleteCommand(3).execute(transactions, ui);

        assertTransactionDescriptions(transactions, "Transport", "Lunch");
    }

    @Test
    public void execute_zeroIndex_throwsException() {
        TransactionList transactions = createTransactionList();

        FinNusException exception = assertThrows(
                FinNusException.class,
                () -> new DeleteCommand(0).execute(transactions, ui));

        assertEquals("Invalid transaction index.", exception.getMessage());
        assertTransactionDescriptions(transactions, "Allowance", "Transport", "Lunch");
    }

    @Test
    public void execute_negativeIndex_throwsException() {
        TransactionList transactions = createTransactionList();

        FinNusException exception = assertThrows(
                FinNusException.class,
                () -> new DeleteCommand(-1).execute(transactions, ui));

        assertEquals("Invalid transaction index.", exception.getMessage());
        assertTransactionDescriptions(transactions, "Allowance", "Transport", "Lunch");
    }

    @Test
    public void execute_indexGreaterThanSize_throwsException() {
        TransactionList transactions = createTransactionList();

        FinNusException exception = assertThrows(
                FinNusException.class,
                () -> new DeleteCommand(4).execute(transactions, ui));

        assertEquals("Invalid transaction index.", exception.getMessage());
        assertTransactionDescriptions(transactions, "Allowance", "Transport", "Lunch");
    }

    @Test
    public void execute_emptyList_throwsException() {
        TransactionList transactions = new TransactionList();

        FinNusException exception = assertThrows(
                FinNusException.class,
                () -> new DeleteCommand(1).execute(transactions, ui));

        assertEquals("Invalid transaction index.", exception.getMessage());
        assertEquals(0, transactions.size());
    }

    @Test
    public void execute_validIndex_showsDeletedTransaction() throws FinNusException {
        TransactionList transactions = createTransactionList();

        String output = runAndCaptureOutput(new DeleteCommand(1), transactions);

        Transaction expectedTransaction = new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("4.50"),
                "Lunch");

        assertTrue(output.contains("Transaction deleted: " + expectedTransaction));
    }

    /**
     * Creates three transactions in chronological order, oldest first.
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
        return transactions;
    }

    /**
     * Checks the number of transactions and their descriptions in chronological order.
     */
    private void assertTransactionDescriptions(
            TransactionList transactions, String... expectedDescriptions) {
        assertEquals(expectedDescriptions.length, transactions.size());

        for (int i = 0; i < expectedDescriptions.length; i++) {
            assertEquals(expectedDescriptions[i], transactions.get(i).getDescription());
        }
    }

    /**
     * Runs the command and returns what it printed, restoring System.out afterwards.
     */
    private String runAndCaptureOutput(Command command, TransactionList transactions) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOutput = System.out;

        try (PrintStream capturedOutput = new PrintStream(
                output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOutput);
            command.execute(transactions, ui);
        } catch (FinNusException e) {
            throw new AssertionError("Command should not fail", e);
        } finally {
            System.setOut(originalOutput);
        }

        return output.toString(StandardCharsets.UTF_8);
    }
}
//@@author
