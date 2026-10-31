//@@author naieel04
package finnus.command;

import java.util.ArrayList;
import java.util.Arrays;
//@@author naieel04
import java.util.List;
import java.util.logging.Logger;

import finnus.transaction.TransactionList;
import finnus.ui.Ui;

/**
 * Displays the available commands and their syntax without changing transactions.
 */
public class HelpCommand extends Command {
    //@@author nkyjStar
    private static final Logger logger = Logger.getLogger(HelpCommand.class.getName());
    //@@author naieel04
    private static final String COMMAND_HEADER = "Command";
    private static final String DESCRIPTION_HEADER = "Description";
    private static final String SYNTAX_HEADER = "Syntax";
    private static final int MAX_TABLE_WIDTH = 80;
    private static final int BORDER_WIDTH = 10;
    private static final int COMMAND_WIDTH = 12;
    private static final int DESCRIPTION_WIDTH = 20;
    private static final int SYNTAX_WIDTH = MAX_TABLE_WIDTH - BORDER_WIDTH
            - COMMAND_WIDTH - DESCRIPTION_WIDTH;
    private static final String ROW_FORMAT = "| %-" + COMMAND_WIDTH + "s | %-" + DESCRIPTION_WIDTH
            + "s | %-" + SYNTAX_WIDTH + "s |";

    /**
     * Usage instructions generated from {@link CommandWord}. Update the enum when
     * adding commands or changing their descriptions or syntax.
     */
    private static final String[] HELP_LINES = createHelpLines();

    /** Builds an ASCII table with lines no wider than 80 columns. */
    private static String[] createHelpLines() {
        List<CommandWord> availableCommands = Arrays.stream(CommandWord.values())
                .filter(CommandWord::isAvailableAfterSetup)
                .toList();
        //@@author naieel04
        String border = "+" + "-".repeat(COMMAND_WIDTH + 2)
                + "+" + "-".repeat(DESCRIPTION_WIDTH + 2)
                + "+" + "-".repeat(SYNTAX_WIDTH + 2) + "+";
        List<String> lines = new ArrayList<>();
        lines.add("Available commands:");
        lines.add(border);
        lines.add(String.format(ROW_FORMAT, COMMAND_HEADER, DESCRIPTION_HEADER, SYNTAX_HEADER));
        lines.add(border);
        for (CommandWord command : availableCommands) {
            //@@author naieel04
            addWrappedRow(lines, command.getWord(), command.getDescription(), command.getUsage());
        }
        lines.add(border);
        lines.add("");
        lines.add("[] means optional.");
        return lines.toArray(String[]::new);
    }

    /** Adds a row, using blank cells on continuation lines when a value wraps. */
    private static void addWrappedRow(List<String> lines, String command, String description, String syntax) {
        List<String> commandLines = wrap(command, COMMAND_WIDTH);
        List<String> descriptionLines = wrap(description, DESCRIPTION_WIDTH);
        List<String> syntaxLines = wrap(syntax, SYNTAX_WIDTH);
        int rowCount = Math.max(commandLines.size(), Math.max(descriptionLines.size(), syntaxLines.size()));
        for (int i = 0; i < rowCount; i++) {
            lines.add(String.format(ROW_FORMAT,
                    getLine(commandLines, i), getLine(descriptionLines, i), getLine(syntaxLines, i)));
        }
    }

    /** Wraps text at spaces and hard-wraps any token wider than its column. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (!word.isEmpty()) {
                int separatorWidth = line.isEmpty() ? 0 : 1;
                int remainingWidth = width - line.length() - separatorWidth;
                if (word.length() <= remainingWidth) {
                    if (separatorWidth > 0) {
                        line.append(' ');
                    }
                    line.append(word);
                    word = "";
                } else if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                } else {
                    lines.add(word.substring(0, width));
                    word = word.substring(width);
                }
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String getLine(List<String> lines, int index) {
        return index < lines.size() ? lines.get(index) : "";
    }

    /** Displays the command reference generated from {@link CommandWord}. */
    //@@author naieel04
    @Override
    public void execute(TransactionList transactions, Ui ui) {
        assert ui != null : "UI must not be null";
        ui.showMessage(HELP_LINES);
        //@@author nkyjStar
        logger.fine("Displayed help message");
    }
}
//@@author
