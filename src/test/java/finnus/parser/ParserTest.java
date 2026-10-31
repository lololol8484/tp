package finnus.parser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import finnus.command.AllowanceCommand;
import finnus.command.BalanceCommand;
import finnus.command.CategoryCommand;
import finnus.command.Command;
import finnus.command.CommandWord;
import finnus.command.DeleteCommand;
import finnus.command.DepositCommand;
import finnus.command.ExitCommand;
import finnus.command.FindCommand;
import finnus.command.ForeignTransactionCommand;
import finnus.command.HelpCommand;
import finnus.command.RateCommand;
import finnus.command.RecentTransactionCommand;
import finnus.command.WithdrawCommand;
import finnus.exception.FinNusException;
import finnus.money.Money;
import finnus.transaction.TransactionList;
import finnus.ui.Ui;

/**
 * Checks command recognition and argument validation in the parser.
 */
class ParserTest {
    private static final String FORMAT = "test format";

    /**
     * Independent user inputs and expected types ensure every enum entry has parser coverage.
     */
    // Map.ofEntries has no limit on the number of entries, unlike Map.of (at most 10).
    private static final Map<CommandWord, CommandExample> COMMAND_EXAMPLES = Map.ofEntries(
            Map.entry(CommandWord.DEPOSIT, new CommandExample("deposit 500 d/Allowance", DepositCommand.class)),
            Map.entry(CommandWord.WITHDRAW, new CommandExample("withdraw 4.50 d/Lunch", WithdrawCommand.class)),
            Map.entry(CommandWord.RECENT, new CommandExample("recent 3", RecentTransactionCommand.class)),
            Map.entry(CommandWord.FIND, new CommandExample("find lunch", FindCommand.class)),
            Map.entry(CommandWord.DELETE, new CommandExample("delete 1", DeleteCommand.class)),
            Map.entry(CommandWord.BALANCE, new CommandExample("balance", BalanceCommand.class)),
            Map.entry(CommandWord.ALLOWANCE, new CommandExample("allowance 5 a/500", AllowanceCommand.class)),
            Map.entry(CommandWord.RATE, new CommandExample("rate JPY 110", RateCommand.class)),
            Map.entry(CommandWord.CATEGORY, new CommandExample("category", CategoryCommand.class)),
            Map.entry(CommandWord.HELP, new CommandExample("help", HelpCommand.class)),
            Map.entry(CommandWord.EXIT, new CommandExample("exit", ExitCommand.class)));

    /**
     * Pairs literal user input with the concrete command the parser must produce.
     */
    private record CommandExample(String input, Class<? extends Command> expectedClass) {
    }

    @ParameterizedTest
    @EnumSource(value = CommandWord.class, names = "OPENING", mode = EnumSource.Mode.EXCLUDE)
    public void parse_registeredCommand_returnsExpectedType(CommandWord command) throws FinNusException {
        CommandExample example = COMMAND_EXAMPLES.get(command);
        assertNotNull(example, "Add a valid input example for " + command);
        assertInstanceOf(example.expectedClass(), Parser.parse(example.input()));

        String[] parts = example.input().split(" ", 2);
        String arguments = parts.length > 1 ? parts[1] : "";
        String mixedCaseWord = parts[0].substring(0, 1).toUpperCase(Locale.ROOT) + parts[0].substring(1);
        String[] wordVariations = {parts[0], parts[0].toUpperCase(Locale.ROOT), mixedCaseWord};
        for (String word : wordVariations) {
            assertInstanceOf(example.expectedClass(), Parser.parse(" \t" + word + "\t" + arguments + " \t"));
        }
    }

    @Test
    @ResourceLock("java.util.Locale")
    public void parse_turkishLocale_acceptsCommandsAndCurrency() throws FinNusException {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertInstanceOf(ExitCommand.class, Parser.parse("EXIT extra"));
            assertInstanceOf(WithdrawCommand.class, Parser.parse("WITHDRAW 5 d/Lunch"));
            assertInstanceOf(DepositCommand.class, Parser.parse("DEPOSIT 5"));
            assertInstanceOf(RateCommand.class, Parser.parse("RATE inr 65"));
            assertEquals("INR", Parser.parseCurrencyCode("inr"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @ParameterizedTest
    @EnumSource(value = CommandWord.class, names = {"DEPOSIT", "WITHDRAW", "RATE", "FIND"})
    public void parse_invalidArguments_reportsRegisteredUsage(CommandWord command) {
        Map<CommandWord, String> invalidInputs = Map.of(
                CommandWord.DEPOSIT, "deposit",
                CommandWord.WITHDRAW, "withdraw 1",
                CommandWord.RATE, "rate JPY",
                CommandWord.FIND, "find   ");
        FinNusException exception = assertThrows(FinNusException.class,
                () -> Parser.parse(invalidInputs.get(command)));
        assertTrue(exception.getMessage().contains("Format: " + command.getUsage()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "delete 0", "delete -1", "delete abc", "delete 1 2",
        "delete 2147483648", "recent 2147483648"})
    public void parse_invalidIntegerArguments_throwsException(String input) {
        assertThrows(FinNusException.class, () -> Parser.parse(input));
    }

    @Test
    public void parse_unknownCommand_preservesErrorMessage() {
        FinNusException exception = assertThrows(FinNusException.class, () -> Parser.parse("  NOPE 123  "));
        assertEquals("Unknown command: 'nope'. Type 'help' to see all commands.", exception.getMessage());
    }

    @Test
    public void parse_depositWithDescription_returnsDepositCommand() throws FinNusException {
        assertInstanceOf(DepositCommand.class, Parser.parse("deposit 500 d/Monthly Allowance"));
    }

    @Test
    public void parse_depositWithoutDescription_returnsDepositCommand() throws FinNusException {
        assertInstanceOf(DepositCommand.class, Parser.parse("deposit 500"));
    }

    @Test
    public void parse_commandWordInUpperCase_isAccepted() throws FinNusException {
        assertInstanceOf(DepositCommand.class, Parser.parse("  DEPOSIT 500  "));
    }

    @Test
    public void parse_validWithdraw_returnsWithdrawCommand() throws FinNusException {
        assertInstanceOf(WithdrawCommand.class, Parser.parse("withdraw 4.50 d/Lunch"));
    }

    @Test
    public void parse_withdrawWithoutDescription_throwsException() {
        FinNusException e = assertThrows(FinNusException.class, () -> Parser.parse("withdraw 4.50"));
        assertTrue(e.getMessage().contains("description"));
    }

    @Test
    public void parse_withdrawWithBlankDescription_throwsException() {
        assertThrows(FinNusException.class, () -> Parser.parse("withdraw 4.50 d/   "));
    }

    @Test
    public void parse_exit_returnsExitCommand() throws FinNusException {
        assertInstanceOf(ExitCommand.class, Parser.parse("exit"));
    }

    @Test
    public void parse_help_returnsHelpCommand() throws FinNusException {
        assertInstanceOf(HelpCommand.class, Parser.parse("help"));
    }

    @Test
    public void parse_helpInUpperCase_returnsHelpCommand() throws FinNusException {
        assertInstanceOf(HelpCommand.class, Parser.parse("HELP"));
    }

    @Test
    public void parse_helpWithSurroundingWhitespace_returnsHelpCommand() throws FinNusException {
        assertInstanceOf(HelpCommand.class, Parser.parse("  help  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"help 123", "help deposit", "help anything else", "  HELP\t123  "})
    public void parse_helpWithArguments_returnsHelpCommand(String input) throws FinNusException {
        assertInstanceOf(HelpCommand.class, Parser.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"exit 123", "exit anything else", "  EXIT\t123  "})
    public void parse_exitWithArguments_returnsExitCommand(String input) throws FinNusException {
        assertInstanceOf(ExitCommand.class, Parser.parse(input));
    }

    @Test
    public void parse_unknownCommand_throwsException() {
        assertThrows(FinNusException.class, () -> Parser.parse("spend 5"));
    }

    @Test
    public void splitAmountAndDescription_amountFirst_splitsCorrectly() throws FinNusException {
        assertArrayEquals(new String[] {"4.50", "Chicken rice"},
                Parser.splitAmountAndDescription("4.50 d/Chicken rice", FORMAT));
    }

    @Test
    public void splitAmountAndDescription_descriptionFirst_splitsCorrectly() throws FinNusException {
        assertArrayEquals(new String[] {"4.50", "Chicken rice"},
                Parser.splitAmountAndDescription("d/Chicken rice 4.50", FORMAT));
    }

    @Test
    public void splitAmountAndDescription_noPrefix_returnsEmptyDescription() throws FinNusException {
        assertArrayEquals(new String[] {"500", ""}, Parser.splitAmountAndDescription("500", FORMAT));
    }

    @Test
    public void splitAmountAndDescription_slashInsideWord_notTreatedAsPrefix() throws FinNusException {
        assertArrayEquals(new String[] {"10", "food and/or drinks"},
                Parser.splitAmountAndDescription("10 d/food and/or drinks", FORMAT));
    }

    @Test
    public void parseAmount_validAmounts_parsedCorrectly() throws FinNusException {
        assertEquals(Money.of("4.50"), Parser.parseAmount("4.50", FORMAT));
        assertEquals(Money.of("500"), Parser.parseAmount("500", FORMAT));
        assertEquals(Money.of("0.01"), Parser.parseAmount("0.01", FORMAT));
        assertEquals(Money.of("1000000"), Parser.parseAmount("1000000", FORMAT));
    }

    @Test
    public void parseAmount_invalidAmounts_throwException() {
        String[] invalidAmounts = {"", "abc", "-5", "0", "0.00", "4.555", "1e3", "NaN", "Infinity", "$5",
            "1000000.01"};
        for (String amount : invalidAmounts) {
            assertThrows(FinNusException.class, () -> Parser.parseAmount(amount, FORMAT),
                    "Expected an exception for amount: '" + amount + "'");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 2", "abc", "4.5", "1e3", "NaN", "Infinity"})
    public void parseRecent_invalidArgumentFormat_throwException(String str) {
        FinNusException e = assertThrows(FinNusException.class, () -> Parser.parse("recent " + str));
        assertEquals("Invalid argument for recent.", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    public void parseRecent_invalidArgumentValue_throwsException(String str) {
        FinNusException e = assertThrows(FinNusException.class, () -> Parser.parse("recent "+ str));
        assertEquals("The length must be positive.", e.getMessage());
    }

    @Test
    public void parse_openingOutsideSetup_isUnknownCommand() {
        FinNusException exception = assertThrows(FinNusException.class, () -> Parser.parse("opening 100"));
        assertTrue(exception.getMessage().contains("Unknown command"));
    }

    @Test
    public void parseNonNegativeAmount_zero_isAccepted() throws FinNusException {
        assertEquals(Money.ZERO, Parser.parseNonNegativeAmount("0", FORMAT));
        assertEquals(Money.ZERO, Parser.parseNonNegativeAmount("0.00", FORMAT));
    }

    @Test
    public void parseAmount_zero_throwsGreaterThanZeroMessage() {
        FinNusException e = assertThrows(FinNusException.class, () -> Parser.parseAmount("0", FORMAT));
        assertEquals("Amount must be greater than 0.", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate", "rate JPY 110", "rate jpy 0.5425", "  RATE  MYR   3.45  "})
    public void parse_validRate_returnsRateCommand(String input) throws FinNusException {
        assertInstanceOf(RateCommand.class, Parser.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate JPY", "rate 110", "rate JPY 110 extra"})
    public void parse_rateWithWrongArgumentCount_throwsException(String input) {
        FinNusException e = assertThrows(FinNusException.class, () -> Parser.parse(input));
        assertTrue(e.getMessage().contains("Format: rate [CURRENCY RATE]"));
    }

    @Test
    public void parseCurrencyCode_lowerCase_returnsUpperCase() throws FinNusException {
        assertEquals("JPY", Parser.parseCurrencyCode("jpy"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"JYP", "YEN", "JP", "1234", ""})
    public void parseCurrencyCode_unknownCode_throwsException(String code) {
        assertThrows(FinNusException.class, () -> Parser.parseCurrencyCode(code));
    }

    @Test
    public void parseCurrencyCode_sgd_throwsException() {
        FinNusException e = assertThrows(FinNusException.class, () -> Parser.parseCurrencyCode("sgd"));
        assertEquals("Amounts are already in SGD, so SGD does not need a rate.", e.getMessage());
    }

    @Test
    public void parseRateValue_validRates_parsedCorrectly() throws FinNusException {
        assertEquals(new BigDecimal("110"), Parser.parseRateValue("110"));
        assertEquals(new BigDecimal("0.5425"), Parser.parseRateValue("0.5425"));
        assertEquals(new BigDecimal("0.000001"), Parser.parseRateValue("0.000001"));
        assertEquals(new BigDecimal("1000000"), Parser.parseRateValue("1000000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000000", "-1", "abc", "1.1234567", "1e3", "1000000.1"})
    public void parseRateValue_invalidRates_throwException(String rate) {
        assertThrows(FinNusException.class, () -> Parser.parseRateValue(rate));
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", " abc", " 123 abc"})
    public void parse_validBalanceCommands_returnsBalanceCommand(String str) {
        Command cmd = assertDoesNotThrow(() -> Parser.parse("balance" + str));
        assertInstanceOf(BalanceCommand.class, cmd);
    }

    @Test
    public void parse_balanceCommandWithLeadingWhitespace_returnsBalanceCommand() {
        Command cmd = assertDoesNotThrow(() -> Parser.parse("  balance"));
        assertInstanceOf(BalanceCommand.class, cmd);
    }

    //@@author chancheese
    @ParameterizedTest
    @ValueSource(strings = {"withdraw 1100 d/Ramen c/JPY", "withdraw 1100 c/JPY d/Ramen",
        "withdraw c/jpy d/Ramen 1100", "withdraw d/Ramen 1100 c/JPY"})
    public void parse_withdrawWithCurrency_returnsForeignTransactionCommand(String input) throws FinNusException {
        assertInstanceOf(ForeignTransactionCommand.class, Parser.parse(input));
    }

    @Test
    public void parse_depositWithCurrencyAndNoDescription_returnsForeignTransactionCommand()
            throws FinNusException {
        assertInstanceOf(ForeignTransactionCommand.class, Parser.parse("deposit 50 c/USD"));
    }

    @Test
    public void parse_currencySgd_returnsNormalCommand() throws FinNusException {
        assertInstanceOf(DepositCommand.class, Parser.parse("deposit 50 c/sgd"));
        assertInstanceOf(WithdrawCommand.class, Parser.parse("withdraw 4.50 d/Lunch c/SGD"));
    }

    @Test
    public void extractCurrency_currencyAnywhere_removesItFromArguments() throws FinNusException {
        assertArrayEquals(new String[]{"JPY", "1100 d/Ramen"}, Parser.extractCurrency("1100 c/JPY d/Ramen"));
        assertArrayEquals(new String[]{"JPY", "1100 d/Ramen"}, Parser.extractCurrency("1100 d/Ramen c/jpy"));
        assertArrayEquals(new String[]{null, "4.50 d/Lunch"}, Parser.extractCurrency("4.50 d/Lunch"));
    }

    @Test
    public void extractCurrency_prefixInsideWord_isNotCurrency() throws FinNusException {
        assertArrayEquals(new String[]{null, "5 d/abc/def"}, Parser.extractCurrency("5 d/abc/def"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"withdraw 1100 d/Ramen c/", "withdraw 1100 d/Ramen c/JYP",
        "withdraw 1100 d/Ramen c/JPY c/USD", "withdraw 1100 c/JPY", "deposit c/JPY",
        "deposit 0 c/JPY", "deposit -5 c/JPY", "deposit 1.234 c/JPY", "deposit 1e3 c/JPY"})
    public void parse_invalidForeignTransaction_throwsException(String input) {
        assertThrows(FinNusException.class, () -> Parser.parse(input));
    }

    @Test
    public void parseForeignAmount_aboveSgdMaximum_isAllowedBeforeConversion() throws FinNusException {
        // 2,000,000 JPY is only about 18,000 SGD, so the SGD limit is checked after conversion.
        assertEquals(new BigDecimal("2000000"), Parser.parseForeignAmount("2000000", FORMAT));
    }

    //@@author nabijonzzz
    @ParameterizedTest
    @ValueSource(strings = {"withdraw 4.50 d/Chicken rice t/food", "withdraw t/food d/Chicken rice 4.50",
        "withdraw 4.50 t/FOOD d/Chicken rice", "withdraw d/Chicken rice 4.50 t/Food"})
    public void parse_withdrawWithCategoryAnywhere_returnsWithdrawCommand(String input) throws FinNusException {
        assertInstanceOf(WithdrawCommand.class, Parser.parse(input));
    }

    @Test
    public void parse_foreignWithdrawWithCategory_returnsForeignTransactionCommand() throws FinNusException {
        assertInstanceOf(ForeignTransactionCommand.class, Parser.parse("withdraw 1100 c/JPY d/Ramen t/food"));
    }

    @Test
    public void extractCategory_categoryAnywhere_removesItFromArguments() throws FinNusException {
        assertArrayEquals(new String[]{"food", "4.50 d/Chicken rice"},
                Parser.extractCategory("4.50 t/food d/Chicken rice"));
        assertArrayEquals(new String[]{null, "4.50 d/Lunch"}, Parser.extractCategory("4.50 d/Lunch"));
        // t/ only counts at the start of a word, like c/ and d/.
        assertArrayEquals(new String[]{null, "5 d/salt/pepper"}, Parser.extractCategory("5 d/salt/pepper"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"withdraw 4.50 d/Lunch t/", "withdraw 4.50 d/Lunch t/food t/bills",
        "withdraw 4.50 d/Lunch t/fo/od", "withdraw 4.50 d/Lunch t/abcdefghijklmnopqrstu",
        "deposit 500 d/Allowance t/food", "deposit t/food 500"})
    public void parse_invalidCategory_throwsException(String input) {
        assertThrows(FinNusException.class, () -> Parser.parse(input));
    }

    @Test
    public void parse_categoryOnDeposit_explainsCategoriesAreForSpending() {
        FinNusException e = assertThrows(FinNusException.class, () -> Parser.parse("deposit 500 t/food"));
        assertTrue(e.getMessage().contains("only for spending"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "CATEGORY", "category Gym", "category bubble-tea",
        "category add", "category list"})
    public void parse_validCategoryCommand_returnsCategoryCommand(String input) throws FinNusException {
        assertInstanceOf(CategoryCommand.class, Parser.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"category two words", "category a/b", "category abcdefghijklmnopqrstu"})
    public void parse_invalidCategoryCommand_throwsException(String input) {
        assertThrows(FinNusException.class, () -> Parser.parse(input));
    }

    //@@author lololol8484
    @Test
    public void parse_categoryWithoutNameListsAndWithNameAdds() throws FinNusException {
        TransactionList transactions = new TransactionList();
        List<String> displayedLines = new ArrayList<>();
        Ui ui = new Ui() {
            @Override
            public void showMessage(String... lines) {
                displayedLines.addAll(List.of(lines));
            }

            @Override
            public void showMessage(List<String> lines) {
                displayedLines.addAll(lines);
            }
        };

        Parser.parse("category").execute(transactions, ui);
        assertTrue(displayedLines.contains("Categories:"));
        assertTrue(transactions.getCategories().getCustom().isEmpty());

        Parser.parse("category Gym").execute(transactions, ui);
        assertEquals(List.of("Gym"), transactions.getCategories().getCustom());
    }
    //@@author nabijonzzz

    //@@author lololol8484
    @ParameterizedTest
    @ValueSource(strings = {"add", "list"})
    public void parse_categoryNameMatchingFormerSubcommand_addsCategory(String name) throws FinNusException {
        TransactionList transactions = new TransactionList();
        Ui ui = new Ui() {
            @Override
            public void showMessage(String... lines) {
                // Category creation has no effect on the ledger output needed by this test.
            }
        };

        Parser.parse("category " + name).execute(transactions, ui);

        assertEquals(List.of(name), transactions.getCategories().getCustom());
    }
    //@@author nabijonzzz

    //@@author lololol8484
    @Test
    public void parse_oldCategoryAddSyntax_isRejected() {
        assertThrows(FinNusException.class, () -> Parser.parse("category add Gym"));
    }
    //@@author nabijonzzz
    @Test
    public void parse_findWithPhrase_returnsFindCommand() throws FinNusException {
        assertInstanceOf(FindCommand.class, Parser.parse("find chicken rice"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"find", "find   ", "FIND"})
    public void parse_findWithoutKeyword_throwsException(String input) {
        assertThrows(FinNusException.class, () -> Parser.parse(input));
    }
    //@@author
}
