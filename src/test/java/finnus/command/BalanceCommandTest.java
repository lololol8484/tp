//@@author lololol8484
package finnus.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import finnus.allowance.Allowance;
import finnus.money.Money;
import finnus.transaction.Transaction;
import finnus.transaction.TransactionList;
import finnus.transaction.TransactionType;
import finnus.ui.Ui;

class BalanceCommandTest {
    private final Ui ui = new Ui();

    @Test
    public void execute_emptyList_showsZeroBalance() {
        TransactionList transactions = new TransactionList();

        String output = runAndCaptureOutput(
                new BalanceCommand(), transactions);

        assertTrue(output.contains("Your current balance is: $0.00"));
    }

    @Test
    public void execute_depositsAndWithdrawals_showsCalculatedBalance() {
        TransactionList transactions = new TransactionList();
        transactions.add(new Transaction(
                TransactionType.DEPOSIT,
                Money.of("100.00"),
                "Allowance"));
        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("25.50"),
                "Lunch"));

        String output = runAndCaptureOutput(
                new BalanceCommand(), transactions);

        assertTrue(output.contains("Your current balance is: $74.50"));
    }

    @Test
    public void execute_openingBalanceAndTransactions_includesOpeningBalance() {
        TransactionList transactions = new TransactionList();
        transactions.setOpeningBalance(Money.of("1000.00"));
        transactions.add(new Transaction(
                TransactionType.DEPOSIT,
                Money.of("100.25"),
                "Allowance"));
        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("25.50"),
                "Groceries"));

        String output = runAndCaptureOutput(
                new BalanceCommand(), transactions);

        assertTrue(output.contains("Your current balance is: $1074.75"));
    }

    @Test
    public void execute_amountAtHalfCent_roundsUp() {
        TransactionList transactions = new TransactionList();
        transactions.add(new Transaction(
                TransactionType.DEPOSIT,
                Money.of("10.005"),
                "Test deposit"));

        String output = runAndCaptureOutput(
                new BalanceCommand(), transactions);

        assertTrue(output.contains("Your current balance is: $10.01"));
    }

    @Test
    public void execute_amountBelowHalfCent_roundsDown() {
        TransactionList transactions = new TransactionList();
        transactions.add(new Transaction(
                TransactionType.DEPOSIT,
                Money.of("10.004"),
                "Test deposit"));

        String output = runAndCaptureOutput(
                new BalanceCommand(), transactions);

        assertTrue(output.contains("Your current balance is: $10.00"));
    }

    @Test
    public void execute_doesNotModifyTransactionsOrBalance() {
        TransactionList transactions = new TransactionList();
        transactions.setOpeningBalance(Money.of("500.00"));
        transactions.add(new Transaction(
                TransactionType.DEPOSIT,
                Money.of("100.00"),
                "Allowance"));
        transactions.add(new Transaction(
                TransactionType.WITHDRAWAL,
                Money.of("20.00"),
                "Lunch"));

        int originalSize = transactions.size();
        Money originalBalance = transactions.getBalance();

        runAndCaptureOutput(new BalanceCommand(), transactions);

        assertEquals(originalSize, transactions.size());
        assertEquals(originalBalance, transactions.getBalance());
    }

    /**
     * Runs the command and returns what it printed, restoring System.out afterwards.
     */
    private String runAndCaptureOutput(
            BalanceCommand command, TransactionList transactions) {
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

    //@@author chancheese
    @Test
    public void execute_allowanceSet_showsCountdown() {
        TransactionList transactions = new TransactionList();
        transactions.setAllowance(new Allowance(5, null, null, LocalDate.of(2026, 10, 5)));

        String output = runAndCaptureOutput(new BalanceCommand(LocalDate.of(2026, 10, 29)), transactions);

        assertTrue(output.contains("Your current balance is: $0.00"));
        assertTrue(output.contains("Next allowance in 7 days (5 Nov 2026)."));
    }

    @Test
    public void execute_noAllowance_showsNoCountdown() {
        String output = runAndCaptureOutput(new BalanceCommand(LocalDate.of(2026, 10, 29)), new TransactionList());

        assertFalse(output.contains("Next allowance"));
    }
    //@@author
}
//@@author
