package finnus.storage;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import finnus.allowance.Allowance;
import finnus.category.CategoryList;
import finnus.currency.ForeignAmount;
import finnus.money.Money;
import finnus.parser.Parser;
import finnus.storage.LedgerSnapshot.StoredTransaction;
import finnus.transaction.TransactionType;

//@@author naieel04
/**
 * Encodes versioned UTF-8 records and recovers valid rows without filesystem or UI access.
 */
public class LedgerCodec {
    //@@author
    private static final Logger logger = Logger.getLogger(LedgerCodec.class.getName());
    //@@author naieel04
    private static final String HEADER = "FINNUS|1";
    //@@author
    private static final String SETUP_PENDING = "SETUP_PENDING";
    //@@author naieel04
    private static final String OPENING = "OPENING";
    private static final String RATE = "RATE";
    private static final String TRANSACTION = "TRANSACTION";
    //@@author chancheese
    private static final int SGD_TRANSACTION_FIELDS = 4;
    private static final int FOREIGN_TRANSACTION_FIELDS = 7;
    //@@author
    //@@author nabijonzzz
    private static final String CATEGORY = "CATEGORY";
    //@@author chancheese
    /** {@code ALLOWANCE|DAY|AMOUNT|CURRENCY|LAST_CREDITED}; amount and currency may be empty. */
    private static final String ALLOWANCE = "ALLOWANCE";
    private static final int ALLOWANCE_FIELDS = 5;
    //@@author nabijonzzz
    /** Transactions saved since categories were added have the category as one extra, last field. */
    private static final int CATEGORY_FIELDS = 1;
    /**
     * Transactions saved since dates were added also have the date as one more, last field,
     * after the category. Dates are only ever saved together with a category, so every field
     * count (4, 5, 6 for SGD; 7, 8, 9 for a foreign currency) still means exactly one format.
     */
    private static final int DATE_FIELDS = 1;
    //@@author

    //@@author naieel04
    /**
     * Distinguishes damaged data from a newer format that must not be overwritten.
     */
    public enum Status {
        //@@author
        /** The save file exists, but first-run setup has not been confirmed. */
        SETUP_PENDING,
        //@@author naieel04
        /** Every non-blank record was valid. */
        CLEAN,
        /** Some records were recovered, but at least one warning was produced. */
        PARTIAL,
        /** No usable records could be recovered. */
        UNRECOVERABLE,
        /** The file declares a format version this application does not support. */
        UNSUPPORTED
    }

    //@@author naieel04
    /**
     * Carries recovered values and safe diagnostics, without including raw financial rows.
     */
    public record DecodeResult(LedgerSnapshot snapshot, List<String> warnings, Status status) {
        /**
         * Prevents subsequent mutation of the warning list.
         */
        public DecodeResult {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Produces deterministic records with Base64 descriptions and a final newline.
     */
    public String encode(LedgerSnapshot snapshot) {
        //@@author
        assert snapshot != null : "Cannot encode a null ledger snapshot";
        //@@author naieel04
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        text.append(OPENING).append('|').append(snapshot.openingBalance().toPlainString()).append('\n');
        new TreeMap<>(snapshot.rates()).forEach((code, rate) -> text.append(RATE).append('|')
                .append(code).append('|').append(rate.toPlainString()).append('\n'));
        //@@author nabijonzzz
        snapshot.customCategories().forEach(name -> text.append(CATEGORY).append('|').append(name).append('\n'));
        //@@author
        //@@author chancheese
        Allowance allowance = snapshot.allowance();
        if (allowance != null) {
            text.append(ALLOWANCE).append('|').append(allowance.day()).append('|')
                    .append(allowance.hasAmount() ? allowance.amount().toPlainString() : "").append('|')
                    .append(allowance.currencyCode() == null ? "" : allowance.currencyCode()).append('|')
                    .append(allowance.lastCredited()).append('\n');
        }
        //@@author
        //@@author naieel04
        for (StoredTransaction transaction : snapshot.transactions()) {
            String description = Base64.getEncoder().encodeToString(
                    transaction.description().getBytes(StandardCharsets.UTF_8));
            text.append(TRANSACTION).append('|').append(transaction.type()).append('|')
                    //@@author
                    .append(transaction.amount().toPlainString()).append('|').append(description);
            //@@author chancheese
            ForeignAmount foreign = transaction.foreignAmount();
            if (foreign != null) {
                text.append('|').append(foreign.currencyCode()).append('|')
                        .append(foreign.amount().toPlainString()).append('|').append(foreign.rate().toPlainString());
            }
            //@@author
            //@@author nabijonzzz
            // Always written, but empty for a deposit, so the field count tells old rows from new ones.
            text.append('|').append(transaction.category() == null ? "" : transaction.category());
            // ISO format (2026-10-30) so the file is easy to read and edit by hand; empty if unknown.
            text.append('|').append(transaction.date() == null ? "" : transaction.date().toString());
            //@@author
            text.append('\n');
        }
        String encoded = text.toString();
        assert encoded.startsWith(HEADER + '\n') && encoded.endsWith("\n")
                : "Encoded ledger must have a header and final newline";
        return encoded;
    }

    //@@author lololol8484
    /** Encodes the persistent state used while first-run setup is incomplete. */
    public String encodeSetupPending() {
        return HEADER + '\n' + SETUP_PENDING + '\n';
    }
    //@@author naieel04

    //@@author naieel04
    /**
     * Recovers valid records; the first valid opening balance or currency record wins.
     * Blank lines are ignored and an optional UTF-8 byte-order mark is accepted.
     */
    public DecodeResult decode(String text) {
        //@@author
        assert text != null : "Cannot decode null storage text";
        //@@author naieel04
        List<String> lines = text.replaceFirst("^\uFEFF", "").lines().toList();
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) {
            boolean newer = !lines.isEmpty() && lines.getFirst().matches("FINNUS\\|[0-9]+");
            return new DecodeResult(LedgerSnapshot.empty(), List.of("Invalid or unsupported storage header."),
                    newer ? Status.UNSUPPORTED : Status.UNRECOVERABLE);
        }
        //@@author
        if (isSetupPending(lines)) {
            return new DecodeResult(LedgerSnapshot.empty(), List.of(), Status.SETUP_PENDING);
        }
        //@@author naieel04
        Records records = new Records();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            try {
                //@@author
                records.currentRow = index + 1;
                //@@author naieel04
                parseRecord(lines.get(index), records);
                records.validRecords++;
            } catch (IllegalArgumentException e) {
                //@@author
                logger.fine("Skipped invalid storage row " + (index + 1) + ": " + e.getMessage());
                //@@author naieel04
                records.warnings.add("Skipped row " + (index + 1) + ": " + e.getMessage() + ".");
            }
        }
        return records.result();
    }

    //@@author
    //@@author lololol8484
    /** Checks whether the only non-blank record after the header marks interrupted setup. */
    private boolean isSetupPending(List<String> lines) {
        List<String> records = lines.subList(1, lines.size()).stream()
                .filter(line -> !line.isBlank())
                .toList();
        return records.size() == 1 && SETUP_PENDING.equals(records.getFirst().trim());
    }
    //@@author naieel04

    /** Dispatches one delimited line to the parser for its record type. */
    //@@author naieel04
    private void parseRecord(String line, Records records) {
        String[] fields = line.split("\\|", -1);
        for (int index = 0; index < fields.length; index++) {
            fields[index] = fields[index].trim();
        }
        switch (fields[0]) {
        case OPENING -> parseOpening(fields, records);
        case RATE -> parseRate(fields, records);
        case TRANSACTION -> parseTransaction(fields, records);
        //@@author
        case CATEGORY -> parseCategory(fields, records);
        //@@author chancheese
        case ALLOWANCE -> parseAllowance(fields, records);
        //@@author naieel04
        default -> throw new IllegalArgumentException("Unknown record type");
        }
    }

    //@@author
    /** Adds the first valid opening balance, rejecting duplicate records. */
    //@@author naieel04
    private void parseOpening(String[] fields, Records records) {
        requireFields(fields, 2);
        //@@author
        Money amount = parseMoney(fields[1]);
        //@@author naieel04
        if (records.hasOpening) {
            throw new IllegalArgumentException("Duplicate opening balance");
        }
        LedgerSnapshot.validateOpeningBalance(amount);
        records.opening = amount;
        records.hasOpening = true;
    }

    //@@author
    /** Validates and adds a currency rate unless a record for that currency already exists. */
    //@@author naieel04
    private void parseRate(String[] fields, Records records) {
        requireFields(fields, 3);
        BigDecimal rate = parseDecimal(fields[2]);
        LedgerSnapshot.validateRate(fields[1], rate);
        if (records.rates.containsKey(fields[1])) {
            throw new IllegalArgumentException("Duplicate currency record");
        }
        records.rates.put(fields[1], rate);
    }

    //@@author
    /**
     * Validates and appends one transaction record. A transaction typed in another currency
     * has three extra fields: the currency code, the amount as typed, and the rate used.
     */
    //@@author naieel04
    private void parseTransaction(String[] fields, Records records) {
        //@@author nabijonzzz
        int baseFields = baseFieldCount(fields.length);
        boolean hasCategory = fields.length > baseFields;
        boolean hasDate = fields.length == baseFields + CATEGORY_FIELDS + DATE_FIELDS;
        String rawCategory = hasCategory ? fields[baseFields] : null;
        LocalDate date = hasDate ? parseDate(fields[baseFields + CATEGORY_FIELDS]) : null;
        //@@author
        //@@author naieel04
        TransactionType type;
        try {
            type = TransactionType.valueOf(fields[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction type");
        }
        //@@author chancheese
        ForeignAmount foreign = baseFields == FOREIGN_TRANSACTION_FIELDS
                ? parseForeignAmount(fields[4], fields[5], fields[6]) : null;
        //@@author
        //@@author nabijonzzz
        if (type == TransactionType.DEPOSIT && rawCategory != null && !rawCategory.isEmpty()) {
            throw new IllegalArgumentException("A deposit cannot have a category");
        }
        // Built with the default category now so that invalid amounts are rejected straight away;
        // the real category is resolved once all CATEGORY records have been read.
        StoredTransaction stored = new StoredTransaction(type, parseMoney(fields[2]), decodeDescription(fields[3]),
                foreign, type == TransactionType.WITHDRAWAL ? CategoryList.DEFAULT_CATEGORY : null, date);
        records.transactions.add(new PendingTransaction(records.currentRow, stored, rawCategory));
        //@@author
    }

    //@@author nabijonzzz
    /**
     * Returns how many fields a transaction row has without its optional category and date fields.
     *
     * @throws IllegalArgumentException If the count matches no known transaction format.
     */
    private int baseFieldCount(int fieldCount) {
        for (int base : new int[]{SGD_TRANSACTION_FIELDS, FOREIGN_TRANSACTION_FIELDS}) {
            if (fieldCount >= base && fieldCount <= base + CATEGORY_FIELDS + DATE_FIELDS) {
                return base;
            }
        }
        throw new IllegalArgumentException("Incorrect field count");
    }

    /**
     * Parses a saved date in ISO format (e.g. 2026-10-30); an empty field means the date is unknown.
     * Uses the same rules as the parser, so a hand-edited file cannot load a date that a command
     * would reject, such as {@code -2026-01-01} or {@code 0001-01-01}.
     */
    private LocalDate parseDate(String text) {
        if (text.isEmpty()) {
            return null;
        }
        if (!text.matches(Parser.DATE_FORMAT_REGEX)) {
            throw new IllegalArgumentException("Invalid date");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date");
        }
        if (date.isBefore(Parser.EARLIEST_DATE)) {
            throw new IllegalArgumentException("Date is before " + Parser.EARLIEST_DATE);
        }
        return date;
    }

    //@@author chancheese
    /**
     * Reads the allowance setting, rejecting invalid values and a second allowance record.
     */
    private void parseAllowance(String[] fields, Records records) {
        requireFields(fields, ALLOWANCE_FIELDS);
        if (!fields[1].matches("\\d{1,2}")) {
            throw new IllegalArgumentException("Invalid allowance day");
        }
        int day = Integer.parseInt(fields[1]);
        BigDecimal amount = fields[2].isEmpty() ? null : parseDecimal(fields[2]);
        String currencyCode = fields[3].isEmpty() ? null : fields[3];
        LocalDate lastCredited = parseDate(fields[4]);
        LedgerSnapshot.validateAllowance(day, amount, currencyCode, lastCredited);
        if (records.allowance != null) {
            throw new IllegalArgumentException("Duplicate allowance record");
        }
        records.allowance = new Allowance(day, amount, currencyCode, lastCredited);
    }
    //@@author nabijonzzz

    /**
     * Adds a custom category, rejecting invalid or duplicate names (ignoring case,
     * including the predefined categories).
     */
    private void parseCategory(String[] fields, Records records) {
        requireFields(fields, 2);
        LedgerSnapshot.validateNewCategory(records.categories, fields[1]);
        records.categories.add(fields[1]);
    }

    /**
     * A decoded transaction whose category is resolved after every record has been read,
     * because a hand-edited file may list a custom category after the transactions using it.
     *
     * @param row The line number, for warnings.
     * @param stored The transaction with the default category.
     * @param rawCategory The category as saved, or null for a row saved before categories existed.
     */
    private record PendingTransaction(int row, StoredTransaction stored, String rawCategory) {
    }
    //@@author

    //@@author chancheese
    /**
     * Validates the foreign-currency fields of a transaction before building the
     * assertion-checked {@link ForeignAmount}.
     */
    private ForeignAmount parseForeignAmount(String code, String amountText, String rateText) {
        BigDecimal amount = parseDecimal(amountText);
        BigDecimal rate = parseDecimal(rateText);
        LedgerSnapshot.validateRate(code, rate);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Foreign amount must be positive");
        }
        return new ForeignAmount(code, amount, rate);
    }
    //@@author

    /** Decodes a Base64 description and rejects malformed or invalid UTF-8 data. */
    //@@author naieel04
    private String decodeDescription(String text) {
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid description encoding");
        }
    }

    //@@author
    /** Parses a plain decimal value, excluding exponent notation and other formats. */
    //@@author naieel04
    private BigDecimal parseDecimal(String text) {
        if (!text.matches("-?\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("Invalid decimal value");
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal value");
        }
    }

    //@@author
    /** Parses an exact monetary value without applying display rounding. */
    private Money parseMoney(String text) {
        return Money.of(parseDecimal(text));
    }

    /** Rejects a record whose field count differs from its format. */
    //@@author naieel04
    private void requireFields(String[] fields, int expected) {
        if (fields.length != expected) {
            throw new IllegalArgumentException("Incorrect field count");
        }
    }

    /**
     * Accumulates valid records before constructing an immutable, validated snapshot.
     */
    private static class Records {
        //@@author
        private Money opening = Money.ZERO;
        //@@author naieel04
        private boolean hasOpening;
        private int validRecords;
        //@@author nabijonzzz
        private int currentRow;
        private final CategoryList categories = new CategoryList();
        private final List<PendingTransaction> transactions = new ArrayList<>();
        //@@author
        //@@author chancheese
        private Allowance allowance;
        //@@author
        //@@author naieel04
        private final Map<String, BigDecimal> rates = new TreeMap<>();
        private final List<String> warnings = new ArrayList<>();

        private DecodeResult result() {
            if (!hasOpening) {
                warnings.add("Opening balance is missing or invalid; using zero. Balances may be incomplete.");
            }
            //@@author nabijonzzz
            List<StoredTransaction> resolved = transactions.stream().map(this::resolveCategory).toList();
            //@@author
            //@@author naieel04
            Status status = validRecords == 0 ? Status.UNRECOVERABLE
                    : warnings.isEmpty() ? Status.CLEAN : Status.PARTIAL;
            //@@author
            LedgerSnapshot snapshot = new LedgerSnapshot(opening, resolved, rates, categories.getCustom(),
                    allowance);
            assert snapshot != null : "Decoded ledger snapshot must exist";
            logger.fine("Decoded ledger with status " + status + " and " + validRecords + " valid rows");
            return new DecodeResult(snapshot, warnings, status);
        }

        //@@author nabijonzzz
        /**
         * Gives a withdrawal its saved category in the spelling it was created with.
         * Rows saved before categories existed silently become Others; an unknown category
         * also becomes Others, with a warning, so the spending itself is not lost.
         */
        private StoredTransaction resolveCategory(PendingTransaction pending) {
            StoredTransaction stored = pending.stored();
            if (stored.type() != TransactionType.WITHDRAWAL || pending.rawCategory() == null) {
                return stored;
            }
            String category = categories.find(pending.rawCategory()).orElse(null);
            if (category == null) {
                warnings.add("Row " + pending.row() + ": unknown category '" + pending.rawCategory()
                        + "', recorded as " + CategoryList.DEFAULT_CATEGORY + ".");
                category = CategoryList.DEFAULT_CATEGORY;
            }
            return new StoredTransaction(stored.type(), stored.amount(), stored.description(),
                    stored.foreignAmount(), category, stored.date());
        }
        //@@author
    }
}
