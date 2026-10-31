package finnus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import finnus.money.Money;
import finnus.storage.FileStorage;
import finnus.storage.LedgerCodec;
import finnus.storage.LedgerSnapshot;
import finnus.storage.LoadResult;
import finnus.storage.Storage;
import finnus.transaction.TransactionType;

/**
 * Exercises command execution, real files and independent restarts as a complete persistence workflow.
 */
class PersistenceIntegrationTest {
    @TempDir
    Path directory;

    @Test
    public void run_twoSessions_restoresBalanceRatesAndRecentOrdering() {
        Path path = directory.resolve("finnus.txt");
        new FinNUS(new ScriptedUi("100.00", "yes", "", "deposit 50.00 d/Allowance", "withdraw 4.50 d/Lunch",
                "rate JPY 110.00", "exit"), new FileStorage(path)).run();
        ScriptedUi second = new ScriptedUi("balance", "recent", "rate", "deposit 1", "delete 2", "exit");
        new FinNUS(second, new FileStorage(path)).run();
        assertTrue(second.output().contains("$145.50"));
        //@@author nabijonzzz
        // Each transaction now starts with the day it was recorded, e.g. [30 Oct 2026], which must survive a restart.
        String datePrefix = "\\[\\d{1,2} [A-Z][a-z]{2} \\d{4}\\] ";
        assertTrue(second.output().matches("(?s).*1\\. " + datePrefix + "\\[Withdrawal\\] -\\$4\\.50 \\(Lunch\\).*"));
        assertTrue(second.output().matches("(?s).*2\\. " + datePrefix
                + "\\[Deposit\\] \\+\\$50\\.00 \\(Allowance\\).*"));
        //@@author
        assertTrue(second.output().contains("1 SGD = 110 JPY"));
        LedgerSnapshot restored = new FileStorage(path).load().snapshot();
        assertEquals(Money.of("151.00"), restored.toTransactionList().getBalance());
        assertEquals(2, restored.transactions().size());
        assertEquals(TransactionType.DEPOSIT, restored.transactions().getLast().type());
        assertEquals(new BigDecimal("110.00"), restored.rates().get("JPY"));
    }

    //@@author lololol8484
    @Test
    public void run_backdatedTransactions_keepsRecentFindDeleteAndStorageChronological() throws IOException {
        Path path = directory.resolve("finnus.txt");
        Clock clock = Clock.fixed(LocalDate.of(2026, 10, 30).atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        ScriptedUi firstRun = new ScriptedUi("100", "yes", "",
                "deposit 10 d/Entry 10 on/2026-10-10",
                "deposit 20 d/Entry 25 first on/2026-10-25",
                "deposit 30 d/Entry 15 on/2026-10-15",
                "deposit 40 d/Entry 25 latest on/2026-10-25", "exit");
        new FinNUS(firstRun, new FileStorage(path), clock).run();

        ScriptedUi secondRun = new ScriptedUi("recent 4", "find entry", "delete 2", "exit");
        new FinNUS(secondRun, new FileStorage(path), clock).run();
        String output = secondRun.output();

        assertLinesAppearInOrder(output,
                "1. [25 Oct 2026] [Deposit] +$40.00 (Entry 25 latest)",
                "2. [25 Oct 2026] [Deposit] +$20.00 (Entry 25 first)",
                "3. [15 Oct 2026] [Deposit] +$30.00 (Entry 15)",
                "4. [10 Oct 2026] [Deposit] +$10.00 (Entry 10)");
        String findOutput = output.substring(output.indexOf("Found 4 transaction(s)"),
                output.indexOf("Transaction deleted:"));
        assertLinesAppearInOrder(findOutput,
                "1. [25 Oct 2026] [Deposit] +$40.00 (Entry 25 latest)",
                "2. [25 Oct 2026] [Deposit] +$20.00 (Entry 25 first)",
                "3. [15 Oct 2026] [Deposit] +$30.00 (Entry 15)",
                "4. [10 Oct 2026] [Deposit] +$10.00 (Entry 10)");
        assertTrue(output.contains("Transaction deleted: [25 Oct 2026] [Deposit] +$20.00 (Entry 25 first)"));

        List<LedgerSnapshot.StoredTransaction> saved = new FileStorage(path).load().snapshot().transactions();
        assertEquals(List.of("Entry 10", "Entry 15", "Entry 25 latest"),
                saved.stream().map(LedgerSnapshot.StoredTransaction::description).toList());
        assertEquals(List.of(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 15),
                        LocalDate.of(2026, 10, 25)),
                saved.stream().map(LedgerSnapshot.StoredTransaction::date).toList());
    }

    @Test
    public void run_unsortedFile_sortsLedgerAtStartupAndNextSave() throws IOException {
        Path path = directory.resolve("finnus.txt");
        LedgerSnapshot unsorted = new LedgerSnapshot(Money.ZERO, List.of(
                new LedgerSnapshot.StoredTransaction(TransactionType.DEPOSIT, Money.of("1"), "Later", null,
                        null, LocalDate.of(2026, 10, 20)),
                new LedgerSnapshot.StoredTransaction(TransactionType.DEPOSIT, Money.of("1"), "Earlier", null,
                        null, LocalDate.of(2026, 10, 10)),
                new LedgerSnapshot.StoredTransaction(TransactionType.DEPOSIT, Money.of("1"), "Same day first", null,
                        null, LocalDate.of(2026, 10, 15)),
                new LedgerSnapshot.StoredTransaction(TransactionType.DEPOSIT, Money.of("1"), "Same day second", null,
                        null, LocalDate.of(2026, 10, 15))), Map.of());
        Files.writeString(path, new LedgerCodec().encode(unsorted));
        String originalContents = Files.readString(path);

        Clock clock = Clock.fixed(LocalDate.of(2026, 10, 30).atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        ScriptedUi readOnlyUi = new ScriptedUi("recent 4", "exit");
        new FinNUS(readOnlyUi, new FileStorage(path), clock).run();

        assertLinesAppearInOrder(readOnlyUi.output(),
                "1. [20 Oct 2026] [Deposit] +$1.00 (Later)",
                "2. [15 Oct 2026] [Deposit] +$1.00 (Same day second)",
                "3. [15 Oct 2026] [Deposit] +$1.00 (Same day first)",
                "4. [10 Oct 2026] [Deposit] +$1.00 (Earlier)");
        assertEquals(originalContents, Files.readString(path), "A read-only run must not rewrite the source file");

        new FinNUS(new ScriptedUi("deposit 5 d/Newest on/2026-10-30", "exit"),
                new FileStorage(path), clock).run();
        List<String> savedDescriptions = new FileStorage(path).load().snapshot().transactions().stream()
                .map(LedgerSnapshot.StoredTransaction::description).toList();
        assertEquals(List.of("Earlier", "Same day first", "Same day second", "Later", "Newest"),
                savedDescriptions);
    }
    //@@author

    @Test
    public void run_setupConfirmation_persistsOpeningBalanceAndSkipsSetupNextTime() {
        Path path = directory.resolve("finnus.txt");
        ScriptedUi setup = new ScriptedUi("67", "yes", "", "exit");
        new FinNUS(setup, new FileStorage(path)).run();

        LedgerSnapshot saved = new FileStorage(path).load().snapshot();
        assertEquals(Money.of("67"), saved.openingBalance());
        assertTrue(saved.transactions().isEmpty());
        assertFalse(new FileStorage(path).load().setupRequired());

        ScriptedUi nextRun = new ScriptedUi("balance", "exit");
        new FinNUS(nextRun, new FileStorage(path)).run();
        assertTrue(nextRun.output().contains("$67.00"));
        assertFalse(nextRun.output().contains("First-time setup"));
    }

    @Test
    public void run_interruptedSetup_keepsPendingMarkerAndRepeatsSetup() throws IOException {
        Path path = directory.resolve("finnus.txt");
        new FinNUS(new ScriptedUi("67"), new FileStorage(path)).run();

        assertTrue(new FileStorage(path).load().setupRequired());

        ScriptedUi retry = new ScriptedUi("67", "yes", "", "exit");
        new FinNUS(retry, new FileStorage(path)).run();
        assertFalse(new FileStorage(path).load().setupRequired());
        assertEquals(Money.of("67"), new FileStorage(path).load().snapshot().openingBalance());
    }

    @Test
    public void run_corruptFileAndReadOnlyCommands_leavesRepairFileUntouched() throws IOException {
        Path path = Files.writeString(directory.resolve("finnus.txt"), "original damaged bytes");
        ScriptedUi ui = new ScriptedUi("help", "balance", "recent", "rate", "opening 0", "exit");
        new FinNUS(ui, new FileStorage(path)).run();
        assertTrue(ui.output().contains("exit without changing"));
        assertEquals("original damaged bytes", Files.readString(path));
        assertTrue(recoveryContents().isEmpty());
        assertFalse(Files.exists(directory.resolve("finnus.txt.bak")));
    }

    @Test
    public void run_freshSessionAfterCorruption_preservesOriginalAndSavesNewRecords() throws IOException {
        Path path = Files.writeString(directory.resolve("finnus.txt"), "damaged");
        new FinNUS(new ScriptedUi("deposit 5", "deposit 1", "exit"), new FileStorage(path)).run();
        assertEquals(List.of("damaged"), recoveryContents());
        ScriptedUi reopened = new ScriptedUi("balance", "exit");
        new FinNUS(reopened, new FileStorage(path)).run();
        assertTrue(reopened.output().contains("$6.00"));
    }

    @Test
    public void run_partialRecovery_keepsValidHistoryAndArchivesDamagedRows() throws IOException {
        String damaged = "FINNUS|1\nOPENING|10\nTRANSACTION|DEPOSIT|2|\nBAD|row\n";
        Path path = Files.writeString(directory.resolve("finnus.txt"), damaged);
        ScriptedUi ui = new ScriptedUi("balance", "rate JPY 110", "exit");
        new FinNUS(ui, new FileStorage(path)).run();
        assertTrue(ui.output().contains("Skipped row 4"));
        assertTrue(ui.output().contains("Balances may be incomplete"));
        assertTrue(ui.output().contains("$12.00"));
        assertEquals(List.of(damaged), recoveryContents());
        LedgerSnapshot loaded = new FileStorage(path).load().snapshot();
        assertEquals(1, loaded.transactions().size());
        assertEquals(new BigDecimal("110"), loaded.rates().get("JPY"));
    }

    @Test
    public void run_missingMainAndValidBackup_readOnlySessionDoesNotWrite() throws IOException {
        String backup = "FINNUS|1\nOPENING|8\n";
        Files.writeString(directory.resolve("finnus.txt.bak"), backup);
        Path path = directory.resolve("finnus.txt");
        ScriptedUi ui = new ScriptedUi("balance");
        new FinNUS(ui, new FileStorage(path)).run();
        assertTrue(ui.output().contains("$8.00"));
        assertFalse(Files.exists(path));
        assertEquals(backup, Files.readString(directory.resolve("finnus.txt.bak")));
    }

    @Test
    public void run_bothFilesCorrupt_preservesBothAcrossSaves() throws IOException {
        Path path = Files.writeString(directory.resolve("finnus.txt"), "broken main");
        Files.writeString(directory.resolve("finnus.txt.bak"), "broken backup");
        new FinNUS(new ScriptedUi("deposit 1", "exit"), new FileStorage(path)).run();
        List<String> preserved = recoveryContents();
        assertTrue(preserved.contains("broken main"));
        assertEquals("broken backup", Files.readString(directory.resolve("finnus.txt.bak")));
        assertEquals(Money.of("1"), new FileStorage(path).load().snapshot().toTransactionList().getBalance());
    }

    @Test
    public void run_interruptedBeforeExit_changesAreAlreadySaved() {
        Path path = directory.resolve("finnus.txt");
        ScriptedUi interrupted = new ScriptedUi("3", "yes", "", "deposit 3") {
            @Override
            public String readCommand() {
                String command = super.readCommand();
                if (command == null) {
                    throw new IllegalStateException("Simulated interruption before shutdown");
                }
                return command;
            }
        };
        assertThrows(IllegalStateException.class, () -> new FinNUS(interrupted, new FileStorage(path)).run());
        assertEquals(Money.of("6"), new FileStorage(path).load().snapshot().toTransactionList().getBalance());
    }

    @Test
    public void run_failedSaves_preservesDiskStateAcrossRestart() throws IOException {
        Path path = Files.writeString(directory.resolve("finnus.txt"), "FINNUS|1\nOPENING|10\n");
        String original = Files.readString(path);
        Storage failing = new Storage() {
            @Override
            public LoadResult load() {
                return new FileStorage(path).load();
            }

            @Override
            public void save(LedgerSnapshot snapshot) throws IOException {
                throw new IOException("Injected disk failure");
            }
        };
        ScriptedUi ui = new ScriptedUi("deposit 5", "balance", "exit");
        new FinNUS(ui, failing).run();
        assertTrue(ui.output().contains("$15.00"));
        assertTrue(ui.output().contains("will be lost after exit"));
        assertEquals(original, Files.readString(path));
        ScriptedUi reopened = new ScriptedUi("balance", "exit");
        new FinNUS(reopened, new FileStorage(path)).run();
        assertTrue(reopened.output().contains("$10.00"));
    }

    private List<String> recoveryContents() throws IOException {
        List<String> contents = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(candidate -> candidate.toString().contains(".corrupt-")).toList()) {
                contents.add(Files.readString(file));
            }
        }
        return contents;
    }

    //@@author lololol8484
    private void assertLinesAppearInOrder(String output, String... expectedLines) {
        int previousIndex = -1;
        for (String line : expectedLines) {
            int currentIndex = output.indexOf(line);
            assertTrue(currentIndex > previousIndex, "Expected output line in order: " + line);
            previousIndex = currentIndex;
        }
    }
    //@@author
}
