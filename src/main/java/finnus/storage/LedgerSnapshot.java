package finnus.storage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import finnus.allowance.Allowance;
import finnus.category.CategoryList;
import finnus.currency.CurrencyRates;
import finnus.currency.ForeignAmount;
import finnus.money.Money;
import finnus.parser.Parser;
import finnus.transaction.Transaction;
import finnus.transaction.TransactionList;
import finnus.transaction.TransactionType;

//@@author naieel04
/**
 * An immutable value copy of all financial state, used for saving and change detection.
 * Decimal scales are retained so persistence never silently rounds a value.
 */
//@@author
public record LedgerSnapshot(Money openingBalance, List<StoredTransaction> transactions,
                             Map<String, BigDecimal> rates, List<String> customCategories,
                             Allowance allowance) {
    //@@author naieel04
    /**
     * Copies mutable collections and rejects values that cannot represent a valid ledger.
     */
    public LedgerSnapshot {
        //@@author
        assert transactions != null : "Transactions must not be null";
        assert rates != null : "Rates must not be null";
        //@@author naieel04
        Objects.requireNonNull(openingBalance);
        validateOpeningBalance(openingBalance);
        transactions = List.copyOf(transactions);
        rates = Map.copyOf(rates);
        rates.forEach(LedgerSnapshot::validateRate);
        //@@author nabijonzzz
        Objects.requireNonNull(customCategories, "Custom categories must not be null");
        customCategories = List.copyOf(customCategories);
        CategoryList categories = buildCategories(customCategories);
        for (StoredTransaction transaction : transactions) {
            String category = transaction.category();
            // Compare exact spelling, so a snapshot always stores each category the way it was created.
            if (category != null && !categories.find(category).orElse("").equals(category)) {
                throw new IllegalArgumentException("Unknown category " + category);
            }
        }
        //@@author
        //@@author chancheese
        if (allowance != null) {
            validateAllowance(allowance.day(), allowance.amount(), allowance.currencyCode(),
                    allowance.lastCredited());
        }
        //@@author
    }

    //@@author chancheese
    /**
     * Creates a snapshot without an allowance.
     */
    public LedgerSnapshot(Money openingBalance, List<StoredTransaction> transactions,
            Map<String, BigDecimal> rates, List<String> customCategories) {
        this(openingBalance, transactions, rates, customCategories, null);
    }
    //@@author

    //@@author nabijonzzz
    /**
     * Creates a snapshot without custom categories.
     */
    public LedgerSnapshot(Money openingBalance, List<StoredTransaction> transactions,
            Map<String, BigDecimal> rates) {
        this(openingBalance, transactions, rates, List.of());
    }

    /**
     * Builds a category list from custom names, rejecting invalid or duplicate names.
     */
    static CategoryList buildCategories(List<String> customCategories) {
        CategoryList categories = new CategoryList();
        for (String name : customCategories) {
            validateNewCategory(categories, name);
            categories.add(name);
        }
        return categories;
    }

    /**
     * Checks that a custom category has a valid format and does not exist yet, ignoring case.
     */
    static void validateNewCategory(CategoryList categories, String name) {
        if (!CategoryList.isValidName(name)) {
            throw new IllegalArgumentException("Invalid category name");
        }
        if (categories.contains(name)) {
            throw new IllegalArgumentException("Duplicate category");
        }
    }
    //@@author

    //@@author naieel04
    /**
     * A transaction value independent of the mutable ledger and console formatting.
     */
    //@@author
    public record StoredTransaction(TransactionType type, Money amount, String description,
                                    ForeignAmount foreignAmount, String category, LocalDate date) {
        //@@author naieel04
        /**
         * Validates external values before they reach assertion-based domain constructors.
         */
        public StoredTransaction {
            Objects.requireNonNull(type, "Transaction type must not be null");
            Objects.requireNonNull(amount, "Transaction amount must not be null");
            Objects.requireNonNull(description, "Description must not be null");
            //@@author
            if (!amount.isPositive()) {
                //@@author naieel04
                throw new IllegalArgumentException("Transaction amount must be positive");
            }
            //@@author
            if (amount.compareTo(Money.MAX_AMOUNT) > 0) {
                throw new IllegalArgumentException("Transaction amount must not exceed "
                        + Money.MAX_AMOUNT.format());
            }
            //@@author chancheese
            // The SGD amount is saved as well as the original amount and rate, so a file
            // edited by hand could disagree with itself; reject it rather than guess.
            if (foreignAmount != null && !foreignAmount.toSgd().equals(amount)) {
                throw new IllegalArgumentException("Transaction amount does not match its foreign amount");
            }
            //@@author
            //@@author nabijonzzz
            if (type == TransactionType.DEPOSIT && category != null) {
                throw new IllegalArgumentException("A deposit cannot have a category");
            }
            if (type == TransactionType.WITHDRAWAL && !CategoryList.isValidName(category)) {
                throw new IllegalArgumentException("A withdrawal must have a valid category");
            }
            //@@author
        }

        //@@author nabijonzzz
        /**
         * Creates a stored transaction with the default category: Others for a withdrawal,
         * none for a deposit.
         */
        public StoredTransaction(TransactionType type, Money amount, String description,
                ForeignAmount foreignAmount) {
            this(type, amount, description, foreignAmount,
                    type == TransactionType.WITHDRAWAL ? CategoryList.DEFAULT_CATEGORY : null);
        }

        /**
         * Creates a stored transaction without a date, as saved before dates were recorded.
         */
        public StoredTransaction(TransactionType type, Money amount, String description,
                ForeignAmount foreignAmount, String category) {
            this(type, amount, description, foreignAmount, category, null);
        }
        //@@author

        //@@author chancheese
        /**
         * Creates a stored transaction that was typed in SGD.
         */
        public StoredTransaction(TransactionType type, Money amount, String description) {
            this(type, amount, description, null);
        }
        //@@author
    }

    //@@author naieel04
    /**
     * Returns an empty ledger with no exchange rates and a zero opening balance.
     */
    public static LedgerSnapshot empty() {
        //@@author
        return new LedgerSnapshot(Money.ZERO, List.of(), Map.of(), List.of(), null);
    }

    //@@author naieel04
    /**
     * Captures values rather than live views so later commands cannot alter this snapshot.
     */
    public static LedgerSnapshot capture(TransactionList ledger) {
        List<StoredTransaction> records = ledger.getAll().stream()
                .map(transaction -> new StoredTransaction(transaction.getType(), transaction.getAmount(),
                        //@@author
                        transaction.getDescription(), transaction.getForeignAmount().orElse(null),
                        transaction.getCategory().orElse(null), transaction.getDate().orElse(null)))
                //@@author naieel04
                .toList();
        //@@author
        return new LedgerSnapshot(ledger.getOpeningBalance(), records, ledger.getRates().getAll(),
                //@@author chancheese
                ledger.getCategories().getCustom(), ledger.getAllowance().orElse(null));
        //@@author
    }

    //@@author naieel04
    /**
     * Builds a fresh mutable ledger with transactions ordered chronologically by date.
     */
    public TransactionList toTransactionList() {
        TransactionList ledger = new TransactionList();
        ledger.setOpeningBalance(openingBalance);
        //@@author nabijonzzz
        customCategories.forEach(ledger.getCategories()::add);
        //@@author
        //@@author naieel04
        for (StoredTransaction record : transactions) {
            //@@author chancheese
            if (record.foreignAmount() != null) {
                ledger.add(new Transaction(record.type(), record.foreignAmount(), record.description(),
                        record.category(), record.date()));
                continue;
            }
            //@@author
            ledger.add(new Transaction(record.type(), record.amount(), record.description(), record.category(),
                    record.date()));
        }
        //@@author naieel04
        rates.forEach(ledger.getRates()::setRate);
        //@@author chancheese
        ledger.setAllowance(allowance);
        //@@author
        return ledger;
    }

    /**
     * Checks the domain rules for a stored opening balance.
     */
    //@@author
    static void validateOpeningBalance(Money amount) {
        if (amount == null || amount.isNegative()) {
            //@@author naieel04
            throw new IllegalArgumentException("Opening balance must not be negative");
        }
        //@@author
        if (amount.compareTo(Money.MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Opening balance must not exceed " + Money.MAX_AMOUNT.format());
        }
    }

    //@@author naieel04
    /**
     * Checks the domain rules for a stored rate without depending on console argument limits.
     */
    static void validateRate(String code, BigDecimal rate) {
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid currency code");
        }
        if (CurrencyRates.BASE_CURRENCY.equals(code) || rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("Rate must be positive and use a foreign currency");
        }
        if (rate.compareTo(Parser.MAX_RATE) > 0) {
            throw new IllegalArgumentException("Rate must not exceed " + Parser.MAX_RATE);
        }
    }

    //@@author chancheese
    /**
     * Checks the values of a stored allowance before they reach the assertion-checked
     * {@link Allowance} constructor, since a save file may have been edited by hand.
     */
    static void validateAllowance(int day, BigDecimal amount, String currencyCode, LocalDate lastCredited) {
        if (day < Allowance.MIN_DAY || day > Allowance.MAX_DAY) {
            throw new IllegalArgumentException("Allowance day must be from 1 to 31");
        }
        if (amount != null && (amount.signum() <= 0 || amount.scale() > 2)) {
            throw new IllegalArgumentException("Allowance amount must be positive with at most 2 decimal places");
        }
        if (amount != null && currencyCode == null && amount.compareTo(Money.MAX_AMOUNT.toBigDecimal()) > 0) {
            throw new IllegalArgumentException("Allowance amount must not exceed " + Money.MAX_AMOUNT.format());
        }
        if (currencyCode != null) {
            if (amount == null) {
                throw new IllegalArgumentException("Allowance currency needs an amount");
            }
            try {
                Currency.getInstance(currencyCode);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid currency code");
            }
            if (CurrencyRates.BASE_CURRENCY.equals(currencyCode)) {
                throw new IllegalArgumentException("SGD allowances are saved without a currency");
            }
        }
        if (lastCredited == null || lastCredited.isBefore(Parser.EARLIEST_DATE)) {
            throw new IllegalArgumentException("Invalid last credited date");
        }
    }
    //@@author
}
//@@author
