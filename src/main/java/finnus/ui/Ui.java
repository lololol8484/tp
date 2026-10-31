package finnus.ui;

import java.util.List;
import java.util.Scanner;

/**
 * Handles all interaction with the user through the console:
 * reading commands and printing responses.
 */
public class Ui {
    private static final String DIVIDER = "____________________________________________________________";

    private final Scanner in = new Scanner(System.in);

    /** Displays the startup greeting. */
    public void showWelcome() {
        showMessage("Welcome to FinNUS! Type a command to get started.");
    }

    /**
     * Reads the next command typed by the user, or returns {@code null} if the input has ended.
     */
    public String readCommand() {
        if (!in.hasNextLine()) {
            return null;
        }
        return in.nextLine();
    }

    //@@author lololol8484
    /** Prints an inline prompt and reads a response without surrounding it with dividers. */
    public String readCommand(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        if (!in.hasNextLine()) {
            return null;
        }
        return in.nextLine();
    }
    //@@author

    /**
     * Prints one or more lines between two dividers.
     *
     * @param lines Messages to show, in display order.
     */
    public void showMessage(String... lines) {
        System.out.println(DIVIDER);
        for (String line : lines) {
            System.out.println(line);
        }
        System.out.println(DIVIDER);
    }

    /**
     * Displays a list of message lines between the console dividers.
     *
     * @param lines Messages to show, in display order.
     */
    public void showMessage(List<String> lines) {
        System.out.println(DIVIDER);
        for (String line : lines) {
            System.out.println(line);
        }
        System.out.println(DIVIDER);
    }

    /** Displays an error message using the standard message layout. */
    public void showError(String message) {
        showMessage("Error: " + message);
    }
}
