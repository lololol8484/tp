package finnus.transaction;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import finnus.money.Money;

public class TransactionListTest {

    private static TransactionList transactions;
    private static Transaction newWithdrawal;

    @BeforeEach
    public void initEach() {
        transactions = new TransactionList();
        transactions.add(new Transaction(TransactionType.DEPOSIT, Money.of(100), ""));
        transactions.add(new Transaction(TransactionType.WITHDRAWAL, Money.of(30), "Food"));
        transactions.add(new Transaction(TransactionType.DEPOSIT, Money.of(30), "January"));

        newWithdrawal = new Transaction(TransactionType.WITHDRAWAL, Money.of(5), "Taxi");
        transactions.add(newWithdrawal);
    }

    @Test
    public void getSubList_startEndProvided_returnsImmutableSublist() {
        List<Transaction> subList = transactions.getSubList(0, transactions.size());
        assertEquals(4, subList.size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> subList.add(new Transaction(TransactionType.WITHDRAWAL, Money.of(30), "Taxi"))
        );
    }

    @ParameterizedTest
    @ValueSource (ints = {4, 5})
    public void getRecentTransactions_lengthMoreThanOrEqualsSize_returnsAllTransactions(int length) {
        List<Transaction> recentTransactions = transactions.getRecentTransactions(length);
        assertEquals(4, recentTransactions.size());
    }

    @Test
    public void getRecentTransactions_lengthEqualsSize_mostRecentTransactionFirst() {
        List<Transaction> recentTransactions = transactions.getRecentTransactions(3);
        assertEquals(
                newWithdrawal,
                recentTransactions.get(0)
        );
    }

    @Test
    public void getRecentTransactions_lengthLessThanSize_returnsSmallerSublist() {
        List<Transaction> recentTransactions = transactions.getRecentTransactions(2);
        assertEquals(2, recentTransactions.size());
    }

    //@@author lololol8484
    @Test
    public void add_backdatedAndUndatedTransactions_keepsChronologicalOrder() {
        TransactionList list = new TransactionList();
        Transaction sameDayFirst = datedDeposit("Same day first", "2026-10-15");
        Transaction sameDaySecond = datedDeposit("Same day second", "2026-10-15");
        Transaction latest = datedDeposit("Latest", "2026-10-20");
        Transaction earlier = datedDeposit("Earlier", "2026-10-10");
        Transaction undatedFirst = new Transaction(TransactionType.DEPOSIT, Money.of("1"), "Undated first");
        Transaction undatedSecond = new Transaction(TransactionType.DEPOSIT, Money.of("1"), "Undated second");

        list.add(sameDayFirst);
        list.add(latest);
        list.add(undatedFirst);
        list.add(earlier);
        list.add(sameDaySecond);
        list.add(undatedSecond);

        assertEquals(List.of(undatedFirst, undatedSecond, earlier, sameDayFirst, sameDaySecond, latest),
                list.getAll());
    }

    @Test
    public void getRecentTransactions_backdatedSameDayAndUndatedEntries_ordersAsSpecified() {
        TransactionList list = new TransactionList();
        Transaction sameDayFirst = datedDeposit("Same day first", "2026-10-15");
        Transaction sameDaySecond = datedDeposit("Same day second", "2026-10-15");
        Transaction latest = datedDeposit("Latest", "2026-10-20");
        Transaction earlier = datedDeposit("Earlier", "2026-10-10");
        Transaction undatedFirst = new Transaction(TransactionType.DEPOSIT, Money.of("1"), "Undated first");
        Transaction undatedSecond = new Transaction(TransactionType.DEPOSIT, Money.of("1"), "Undated second");
        list.add(sameDayFirst);
        list.add(latest);
        list.add(undatedFirst);
        list.add(earlier);
        list.add(sameDaySecond);
        list.add(undatedSecond);

        assertEquals(List.of(latest, sameDaySecond, sameDayFirst, earlier, undatedSecond, undatedFirst),
                list.getRecentTransactions(list.size()));
        assertEquals(List.of(latest, sameDaySecond, sameDayFirst), list.getRecentTransactions(3));
    }

    @Test
    public void add_nullTransaction_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new TransactionList().add(null));
    }

    private Transaction datedDeposit(String description, String date) {
        return new Transaction(TransactionType.DEPOSIT, Money.of("1"), description, null, LocalDate.parse(date));
    }
    //@@author

    @Test
    public void getRecentTransactions_noTransactionsMade_returnsEmptyList() {
        TransactionList emptyTransactionList = new TransactionList();
        List<Transaction> recentTransactions = assertDoesNotThrow(() -> emptyTransactionList.getRecentTransactions(0));
        assertEquals(Collections.emptyList(), recentTransactions);
    }

    // The balance tests below build their own list so they do not depend on the shared fixture.

    @Test
    public void getBalance_newList_isZero() {
        assertEquals(Money.ZERO, new TransactionList().getBalance());
    }

    @Test
    public void getBalance_openingPlusTransactions_addsUpCorrectly() {
        TransactionList list = new TransactionList();
        list.setOpeningBalance(Money.of("10.10"));
        list.add(new Transaction(TransactionType.DEPOSIT, Money.of("0.20"), ""));
        list.add(new Transaction(TransactionType.WITHDRAWAL, Money.of("5.05"), "Snack"));

        assertEquals(Money.of("5.25"), list.getBalance());
    }

    @Test
    public void getBalance_spendingMoreThanOpening_canBeNegative() {
        TransactionList list = new TransactionList();
        list.setOpeningBalance(Money.of("5"));
        list.add(new Transaction(TransactionType.WITHDRAWAL, Money.of("8"), "Taxi"));

        assertEquals(Money.of("-3"), list.getBalance());
    }

    @Test
    public void setOpeningBalance_existingTransactions_doesNotAddTransaction() {
        TransactionList list = new TransactionList();
        list.add(new Transaction(TransactionType.DEPOSIT, Money.of("50"), ""));
        list.setOpeningBalance(Money.of("100"));

        assertEquals(1, list.size());
        assertEquals(Money.of("100"), list.getOpeningBalance());
    }

    @Test
    public void getBalance_balance_returnsPositiveBalance() {
        Money balance = transactions.getBalance();
        assertEquals(Money.of(95), balance);
    }

    @Test
    public void getBalance_balance_returnsNegativeBalance() {
        transactions.add(new Transaction(TransactionType.WITHDRAWAL, Money.of(205), ""));
        Money balance = transactions.getBalance();
        assertEquals(Money.of(-110), balance);
    }
}
