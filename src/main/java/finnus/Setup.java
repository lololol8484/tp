package finnus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

import finnus.command.AllowanceCommand;
import finnus.command.OpeningBalanceCommand;
import finnus.exception.FinNusException;
import finnus.money.Money;
import finnus.parser.Parser;
import finnus.transaction.TransactionList;
import finnus.ui.Ui;

/**
 * Guides a first-time user through choosing and confirming the opening balance,
 * then optionally setting up their monthly allowance.
 */
//@@author lololol8484
public class Setup {
    //@@author nkyjStar
    private static final Logger logger = Logger.getLogger(Setup.class.getName());
    //@@author lololol8484

    private enum Confirmation {
        CONFIRMED, NOT_CONFIRMED
    }

    //@@author chancheese
    /**
     * Runs setup using the real current date.
     *
     * @see #run(Ui, TransactionList, LocalDate)
     */
    public boolean run(Ui ui, TransactionList transactions) {
        return run(ui, transactions, LocalDate.now());
    }
    //@@author lololol8484

    /**
     * Prompts for an opening balance until the user confirms it or input ends, then asks
     * for the allowance, which the user may skip.
     *
     * @param ui The console interface used for prompts and responses.
     * @param transactions The ledger whose opening balance is being initialized.
     * @param today The current date, used to work out the allowance cycle.
     * @return {@code true} if the user confirmed a balance; {@code false} if input ended first.
     */
    //@@author chancheese
    public boolean run(Ui ui, TransactionList transactions, LocalDate today) {
        //@@author lololol8484
        assert ui != null : "UI must not be null";
        assert transactions != null : "Transaction list must not be null";
        assert transactions.size() == 0 : "First-run setup must not start with transactions";
        ui.showMessage("First-time setup", "Enter your current opening balance in SGD.",
                "This is your starting balance and will not be recorded as a transaction.");
        while (true) {
            String input = ui.readCommand("Opening balance (SGD): ");
            if (input == null) {
                //@@author nkyjStar
                logger.fine("First-run setup ended before an opening balance was entered");
                //@@author lololol8484
                return false;
            }
            Money amount;
            try {
                amount = Parser.parseNonNegativeAmount(input.trim(), "AMOUNT");
            } catch (FinNusException e) {
                //@@author nkyjStar
                logger.fine("Rejected invalid opening balance during setup");
                //@@author lololol8484
                ui.showError(e.getMessage());
                continue;
            }
            Optional<Confirmation> confirmation = confirmBalance(ui, amount);
            if (confirmation.isEmpty()) {
                return false;
            }
            if (confirmation.get() == Confirmation.NOT_CONFIRMED) {
                continue;
            }

            new OpeningBalanceCommand(amount).execute(transactions, ui);
            assert transactions.getOpeningBalance().compareTo(amount) == 0
                    : "Setup must store the entered opening balance";
            //@@author nkyjStar
            logger.info("First-run opening balance confirmed");
            //@@author lololol8484
            //@@author chancheese
            askForAllowance(ui, transactions, today);
            //@@author lololol8484
            return true;
        }
    }

    //@@author chancheese
    /**
     * Asks for the allowance day and then its amount in SGD. Pressing Enter skips either one,
     * and so does ending the input, since the opening balance has already been confirmed.
     * Foreign currencies need a rate first, so they can only be set later with the command.
     */
    private void askForAllowance(Ui ui, TransactionList transactions, LocalDate today) {
        ui.showMessage("Optional: when does your monthly allowance arrive?",
                "FinNUS can count the days until it arrives and add it for you each month.",
                "Press Enter to skip; you can set it later with: " + AllowanceCommand.EXAMPLE);
        Optional<Integer> day = askUntilValid(ui, "Allowance day (1-31): ", Parser::parseAllowanceDay);
        if (day.isEmpty()) {
            logger.fine("Allowance skipped during setup");
            return;
        }
        Optional<Money> amount = askUntilValid(ui, "Allowance amount in SGD (Enter to only count days): ",
                text -> Parser.parseAmount(text, "AMOUNT"));
        BigDecimal value = amount.map(Money::toBigDecimal).orElse(null);
        try {
            AllowanceCommand.set(day.get(), value, null, today).execute(transactions, ui);
        } catch (FinNusException e) {
            // Only foreign amounts can fail when set, and setup only takes SGD.
            assert false : "Setting an SGD allowance must not fail: " + e.getMessage();
            ui.showError(e.getMessage());
            return;
        }
        logger.info("Allowance set during setup");
    }

    /**
     * Reads one answer, asking again until it is valid.
     *
     * @return The parsed answer, or an empty Optional if the user pressed Enter or input ended.
     */
    private <T> Optional<T> askUntilValid(Ui ui, String prompt, AnswerParser<T> parser) {
        while (true) {
            String input = ui.readCommand(prompt);
            if (input == null || input.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(parser.parse(input.trim()));
            } catch (FinNusException e) {
                ui.showError(e.getMessage());
            }
        }
    }

    /** Converts one answer, like a parser method that may reject the text. */
    @FunctionalInterface
    private interface AnswerParser<T> {
        T parse(String text) throws FinNusException;
    }
    //@@author lololol8484

    /** Repeats the confirmation prompt until the user answers yes or no. */
    private Optional<Confirmation> confirmBalance(Ui ui, Money amount) {
        while (true) {
            String answer = ui.readCommand("Confirm opening balance "
                    + amount.format() + " SGD? [yes/no]: ");
            if (answer == null) {
                //@@author nkyjStar
                logger.fine("First-run setup ended before the opening balance was confirmed");
                //@@author lololol8484
                return Optional.empty();
            }
            switch (answer.trim().toLowerCase(Locale.ROOT)) {
            case "yes", "y" -> {
                return Optional.of(Confirmation.CONFIRMED);
            }
            case "no", "n" -> {
                //@@author nkyjStar
                logger.fine("User requested another opening balance entry");
                //@@author lololol8484
                return Optional.of(Confirmation.NOT_CONFIRMED);
            }
            default -> ui.showError("Please answer yes or no.");
            }
        }
    }
}
//@@author
