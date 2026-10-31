package finnus.transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import finnus.allowance.Allowance;
import finnus.category.CategoryList;
import finnus.currency.CurrencyRates;
import finnus.money.Money;

//@@author lololol8484
/**
 * Holds recorded transactions chronologically by date (oldest first), together with the
 * opening balance the ledger starts from. Undated legacy transactions come before dated ones.
 * Other features (balance, recent, delete, storage) should read and modify
 * transactions only through this class.
 */
//@@author
public class TransactionList {
    private static final Logger logger = Logger.getLogger(TransactionList.class.getName());

    private final List<Transaction> transactions = new ArrayList<>();

    //@@author lololol8484
    /** Sorts undated legacy transactions first, followed by dates from oldest to newest. */
    private static final Comparator<Transaction> CHRONOLOGICAL_ORDER = Comparator.comparing(
            (Transaction transaction) -> transaction.getDate().orElse(null),
            Comparator.nullsFirst(Comparator.naturalOrder()));
    //@@author

    /**
     * The money the user had before recording any transactions, in SGD.
     * It is a starting point, not a transaction, so it never appears in the list
     * and does not shift the indices used by {@code recent} and {@code delete}.
     * Defaults to 0 for users who never set it.
     */
    private Money openingBalance = Money.ZERO;

    /**
     * Exchange rates the user has set, used to convert foreign amounts to SGD.
     */
    private final CurrencyRates rates = new CurrencyRates();

    //@@author nabijonzzz
    /**
     * Categories a withdrawal can be tagged with, including custom ones the user added.
     */
    private final CategoryList categories = new CategoryList();
    //@@author

    //@@author chancheese
    /**
     * The user's monthly allowance, or null if it has not been set. It is a setting, not a
     * transaction, so it never shifts the indices used by {@code recent} and {@code delete}.
     */
    private Allowance allowance;
    //@@author

    //@@author lololol8484
    /** Inserts a transaction by date, keeping earlier entries first and same-day entries stable. */
    public void add(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction must not be null");
        int oldSize = transactions.size();
        int insertionIndex = 0;
        while (insertionIndex < transactions.size()
                && CHRONOLOGICAL_ORDER.compare(transactions.get(insertionIndex), transaction) <= 0) {
            insertionIndex++;
        }
        transactions.add(insertionIndex, transaction);
        assert transactions.size() == oldSize + 1 : "Adding a transaction must increase the list size";
        assert insertionIndex == 0
                || CHRONOLOGICAL_ORDER.compare(transactions.get(insertionIndex - 1), transaction) <= 0
                : "Transaction must not precede the prior chronological entry";
        assert insertionIndex == transactions.size() - 1
                || CHRONOLOGICAL_ORDER.compare(transaction, transactions.get(insertionIndex + 1)) <= 0
                : "Transaction must not follow the next chronological entry";
        logger.fine("Inserted transaction at chronological position " + insertionIndex
                + " for " + transaction.getDate().map(Object::toString).orElse("an undated entry"));
    }
    //@@author

    /**
     * Returns the transaction at the given zero-based index.
     */
    public Transaction get(int index) {
        return transactions.get(index);
    }

    /** Returns the number of recorded transactions. */
    public int size() {
        return transactions.size();
    }

    /** Returns the rate collection used to convert foreign currencies. */
    public CurrencyRates getRates() {
        return rates;
    }

    //@@author nabijonzzz
    /** Returns the categories that withdrawals can be tagged with. */
    public CategoryList getCategories() {
        return categories;
    }
    //@@author

    //@@author chancheese
    /** Returns the user's allowance, or an empty Optional if it has not been set. */
    public Optional<Allowance> getAllowance() {
        return Optional.ofNullable(allowance);
    }

    /**
     * Replaces the allowance setting.
     *
     * @param allowance The new allowance, or null to remove it.
     */
    public void setAllowance(Allowance allowance) {
        this.allowance = allowance;
    }
    //@@author

    /**
     * Returns a read-only view of all transactions, so callers cannot modify
     * the list directly.
     */
    public List<Transaction> getAll() {
        return List.copyOf(transactions);
    }

    /**
     * Returns an immutable sub-list of transactions.
     *
     * @param start The starting index of the sublist (inclusive)
     * @param end The ending index of the sublist (exclusive)
     */
    public List<Transaction> getSubList(int start, int end) {
        assert start >= 0 && start < end && end <= transactions.size()
                : "Sublist bounds must be within the transaction list";
        return Collections.unmodifiableList(transactions.subList(start, end));
    }

    /**
     * Returns a read-only list of the most recent transactions of a specified
     * length. If the list size is smaller than the length, returns the list
     * itself. The most recent transaction is at index 0
     *
     * @param length The number of transactions to show, starting from the most
     *     recent one.
     * @return An immutable list ordered from newest to oldest.
     */
    public List<Transaction> getRecentTransactions(int length) {
        // Safeguard against empty transactions list
        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }

        // Safeguard against lists of size smaller than length
        int fromIndex = Math.max(0, transactions.size() - length);

        // Reverse subList so that the most recent transaction is at the start of the list
        List<Transaction> subList = getSubList(fromIndex, transactions.size()).reversed();
        return subList;
    }

    /**
     * Deletes the transaction at the given zero-based index and returns it.
     *
     * @param index The zero-based index of the transaction to delete.
     * @return The deleted transaction.
     */
    public Transaction delete(int index) {
        assert index >= 0 && index < transactions.size() : "Transaction index must be within the list";
        int oldSize = transactions.size();
        Transaction removed = transactions.remove(index);
        assert transactions.size() == oldSize - 1 : "Deleting a transaction must reduce the list size";
        assert index == 0 || index == transactions.size()
                || CHRONOLOGICAL_ORDER.compare(transactions.get(index - 1), transactions.get(index)) <= 0
                : "Deleting a transaction must preserve chronological order";
        return removed;
    }

    /** Returns the starting balance before any transactions are applied. */
    public Money getOpeningBalance() {
        return openingBalance;
    }

    /**
     * Replaces the opening balance. Because the balance is always recalculated from
     * the opening balance, changing it shifts the current balance by the same difference.
     *
     * @param openingBalance The new opening balance; must not be negative.
     */
    public void setOpeningBalance(Money openingBalance) {
        assert openingBalance != null && !openingBalance.isNegative() : "Opening balance must not be negative";
        this.openingBalance = openingBalance;
    }

    /**
     * Returns the current balance: the opening balance plus all deposits minus all withdrawals.
     * This is the single place the balance is calculated, so every feature reports the same figure.
     */
    public Money getBalance() {
        return transactions.stream()
                .map(Transaction::getSignedAmount)
                .reduce(openingBalance, Money::add);
    }
}
