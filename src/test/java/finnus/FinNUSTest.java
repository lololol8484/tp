package finnus;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import finnus.allowance.Allowance;
import finnus.money.Money;
import finnus.storage.LedgerSnapshot;
import finnus.storage.LoadResult;
import finnus.storage.Storage;

/**
 * Verifies autosave timing and failure handling independently of filesystem behavior.
 */
class FinNUSTest {
    @Test
    public void run_mutatingCommands_savesEachChangedSnapshot() {
        RecordingStorage storage = new RecordingStorage();
        ScriptedUi ui = new ScriptedUi("deposit 50", "withdraw 5 d/Lunch",
                "rate JPY 110", "delete 1", "exit");
        new FinNUS(ui, storage).run();
        assertEquals(4, storage.saved.size());
        LedgerSnapshot last = storage.saved.getLast();
        assertEquals(Money.of("50"), last.toTransactionList().getBalance());
        assertEquals(new BigDecimal("110"), last.rates().get("JPY"));
        assertEquals(1, last.transactions().size());
    }

    @Test
    public void run_readOnlyAndInvalidCommands_doesNotSave() {
        RecordingStorage storage = new RecordingStorage();
        ScriptedUi ui = new ScriptedUi("help", "recent", "balance", "rate", "unknown", "withdraw 0", "exit");
        new FinNUS(ui, storage).run();
        assertEquals(0, storage.attempts);
    }

    //@@author lololol8484
    @Test
    public void run_repeatedRate_doesNotWriteAgain() {
        RecordingStorage storage = new RecordingStorage();
        new FinNUS(new ScriptedUi("rate JPY 110", "rate JPY 110", "exit"),
                storage).run();
        assertEquals(1, storage.attempts);
    }
    //@@author

    @Test
    public void run_failedSave_retainsStateAndRetriesAfterReadOnlyCommand() {
        RecordingStorage storage = new RecordingStorage();
        storage.failuresRemaining = 1;
        ScriptedUi ui = new ScriptedUi("deposit 10", "balance", "exit");
        new FinNUS(ui, storage).run();
        assertEquals(2, storage.attempts);
        assertEquals(Money.of("10"), storage.saved.getFirst().toTransactionList().getBalance());
        assertTrue(ui.output().contains("Automatic save failed"));
        assertTrue(ui.output().contains("Your current balance is: $10.00"));
    }

    @Test
    public void run_exitAfterFailure_retriesPendingSave() {
        RecordingStorage storage = new RecordingStorage();
        storage.failuresRemaining = 1;
        new FinNUS(new ScriptedUi("deposit 10", "exit"), storage).run();
        assertEquals(2, storage.attempts);
        assertEquals(1, storage.saved.size());
    }

    @Test
    public void run_endOfInputAfterFailure_retriesPendingSave() {
        RecordingStorage storage = new RecordingStorage();
        storage.failuresRemaining = 1;
        new FinNUS(new ScriptedUi("deposit 10"), storage).run();
        assertEquals(2, storage.attempts);
        assertEquals(1, storage.saved.size());
    }

    @Test
    public void run_finalSaveFailure_warnsOfUnsavedChanges() {
        RecordingStorage storage = new RecordingStorage();
        storage.failuresRemaining = 10;
        ScriptedUi ui = new ScriptedUi("deposit 10", "exit");
        new FinNUS(ui, storage).run();
        assertEquals(2, storage.attempts);
        assertTrue(ui.output().contains("will be lost after exit"));
        assertTrue(storage.saved.isEmpty());
    }

    @Test
    public void run_savingDisabled_neverOverwritesStorage() {
        RecordingStorage storage = new RecordingStorage();
        storage.loaded = new LoadResult(LedgerSnapshot.empty(), List.of("Read failed"), false);
        ScriptedUi ui = new ScriptedUi("deposit 10", "exit");
        new FinNUS(ui, storage).run();
        assertEquals(0, storage.attempts);
        assertTrue(ui.output().contains("Read failed"));
        assertTrue(ui.output().contains("Saving is disabled"));
        assertTrue(ui.output().contains("will be lost after exit"));
    }

    @Test
    public void run_loadedState_isAvailableBeforeCommands() {
        RecordingStorage storage = new RecordingStorage();
        storage.loaded = new LoadResult(new LedgerSnapshot(Money.of("12.30"), List.of(),
                Map.of("JPY", new BigDecimal("110"))), List.of(), true);
        ScriptedUi ui = new ScriptedUi("balance", "rate", "exit");
        new FinNUS(ui, storage).run();
        assertTrue(ui.output().contains("$12.30"));
        assertTrue(ui.output().contains("1 SGD = 110 JPY"));
        assertEquals(0, storage.attempts);
    }
    //@@author chancheese
    /** Returns a clock that is always at the start of the given day. */
    private static Clock fixedOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    @Test
    public void run_allowanceArrivedWhileClosed_addsAndSavesItAtStartup() {
        RecordingStorage storage = new RecordingStorage();
        Allowance allowance = new Allowance(5, new BigDecimal("500"), null, LocalDate.of(2026, 9, 5));
        storage.loaded = new LoadResult(new LedgerSnapshot(Money.ZERO, List.of(), Map.of(), List.of(), allowance),
                List.of(), true);
        ScriptedUi ui = new ScriptedUi("exit");

        new FinNUS(ui, storage, fixedOn(LocalDate.of(2026, 11, 5))).run();

        assertTrue(ui.output().contains("Allowance added: [5 Oct 2026] [Deposit] +$500.00 (Allowance)"));
        assertTrue(ui.output().contains("Allowance added: [5 Nov 2026] [Deposit] +$500.00 (Allowance)"));
        assertEquals(1, storage.attempts);
        LedgerSnapshot saved = storage.saved.getLast();
        assertEquals(2, saved.transactions().size());
        assertEquals(LocalDate.of(2026, 11, 5), saved.allowance().lastCredited());
    }

    @Test
    public void run_rateSetForWaitingAllowance_addsItAfterTheCommand() {
        RecordingStorage storage = new RecordingStorage();
        Allowance allowance = new Allowance(5, new BigDecimal("60000"), "JPY", LocalDate.of(2026, 10, 5));
        storage.loaded = new LoadResult(new LedgerSnapshot(Money.ZERO, List.of(), Map.of(), List.of(), allowance),
                List.of(), true);
        ScriptedUi ui = new ScriptedUi("rate JPY 120", "exit");

        new FinNUS(ui, storage, fixedOn(LocalDate.of(2026, 11, 6))).run();

        assertTrue(ui.output().contains("Your allowance for 5 Nov 2026 was not added yet."));
        assertTrue(ui.output().contains("Allowance added: [5 Nov 2026] [Deposit] +$500.00 (Allowance) [JPY 60000]"));
        assertEquals(1, storage.saved.getLast().transactions().size());
    }

    @Test
    public void run_allowanceCommand_savesSettingAndShowsCountdownInBalance() {
        RecordingStorage storage = new RecordingStorage();
        ScriptedUi ui = new ScriptedUi("allowance 5 a/500", "balance", "allowance off", "exit");

        new FinNUS(ui, storage, fixedOn(LocalDate.of(2026, 10, 29))).run();

        assertEquals(2, storage.saved.size(), "Setting and stopping the allowance are both saved");
        assertEquals(5, storage.saved.getFirst().allowance().day());
        assertEquals(null, storage.saved.getLast().allowance());
        assertTrue(ui.output().contains("Next allowance in 7 days (5 Nov 2026)."));
    }
    //@@author

    /**
     * Records successful snapshots and fails a configurable number of saves without a mocking library.
     */
    private static class RecordingStorage implements Storage {
        private LoadResult loaded = new LoadResult(LedgerSnapshot.empty(), List.of(), true);
        private final List<LedgerSnapshot> saved = new ArrayList<>();
        private int attempts;
        private int failuresRemaining;

        @Override
        public LoadResult load() {
            return loaded;
        }

        @Override
        public void save(LedgerSnapshot snapshot) throws IOException {
            attempts++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IOException("Injected save failure");
            }
            saved.add(snapshot);
        }
    }
}
