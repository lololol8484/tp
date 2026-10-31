//@@author lololol8484
package finnus.command;

import java.util.logging.Logger;

import finnus.exception.FinNusException;
import finnus.transaction.Transaction;
import finnus.transaction.TransactionList;
import finnus.ui.Ui;

/**
 * Deletes a transaction from the transaction list.
 */
public class DeleteCommand extends Command {
    private static final Logger logger = Logger.getLogger(DeleteCommand.class.getName());

    private final int index;

    /**
     * Creates a DeleteCommand with the specified index of the transaction to delete.
     *
     * @param index The recent list based index of the transaction to delete.
     */
    public DeleteCommand(int index) {
        this.index = index;
    }

    /**
     * Executes the delete command by removing the transaction at the specified index
     * from the transaction list and displaying a confirmation message.
     *
     * @param transactions The transaction list to delete from.
     * @param ui The user interface to display messages.
     * @throws FinNusException If the index is invalid.
     */
    @Override
    public void execute(TransactionList transactions, Ui ui) throws FinNusException {
        assert transactions != null : "Transaction list must not be null";
        assert ui != null : "UI must not be null";
        if (index < 1 || index > transactions.size()) {
            logger.warning("Failed to delete transaction: invalid index " + index
                + " for " + transactions.size() + " transaction(s).");
            throw new FinNusException("Invalid transaction index.");
        }
        int sizeBeforeDeletion = transactions.size();
        // Convert recent list based index to 0-based index for internal list operations
        Transaction deletedTransaction = transactions.delete(transactions.size() - index);
        assert transactions.size() == sizeBeforeDeletion - 1
            : "Deleting a transaction should decrease the list size by one";
        logger.info("Deleted transaction at recent-list index " + index
            + ". " + transactions.size() + " transaction(s) remaining.");
        ui.showMessage("Transaction deleted: " + deletedTransaction);
    }
}
//@@author
