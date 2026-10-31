//@@author naieel04
package finnus.storage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Saves ledger snapshots beside the JAR using atomic replacement and a previous-state backup.
 */
public class FileStorage implements Storage {
    //@@author nkyjStar
    private static final Logger logger = Logger.getLogger(FileStorage.class.getName());
    //@@author naieel04
    private static final String FILE_NAME = "finnus.txt";

    private final Path path;
    private final LedgerCodec codec = new LedgerCodec();
    private final AtomicReplacement replacement;
    /** True after a clean main file was loaded or saved, so it can safely become a backup. */
    //@@author naieel04
    private boolean knownClean;
    /** Tracks whether load has initialized recovery and overwrite policy. */
    //@@author naieel04
    private boolean initialized;
    /** False when the source format or read failure makes overwriting unsafe. */
    //@@author naieel04
    private boolean savingAllowed = true;
    /** True when the main file records that first-run setup is still incomplete. */
    private boolean setupRequired;
    /** Requests preserving damaged source bytes before the next successful replacement. */
    //@@author naieel04
    private boolean preserveOriginal;
    /** Prevents the same damaged source from being copied more than once per session. */
    //@@author naieel04
    private final Set<Path> preservedSources = new HashSet<>();

    /**
     * Creates storage for the supplied file; useful for isolated tests and alternate launchers.
     */
    public FileStorage(Path path) {
        this(path, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    /** Creates storage with an injected replacement strategy for controlled failure handling. */
    //@@author naieel04
    FileStorage(Path path, AtomicReplacement replacement) {
        this.path = path.toAbsolutePath().normalize();
        this.replacement = replacement;
    }

    /**
     * Returns the save location beside a packaged JAR, or in the working directory during development.
     */
    public static Path defaultPath() {
        try {
            Path location = Path.of(FileStorage.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return resolvePath(location, Path.of(""));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot determine the application location", e);
        }
    }

    /** Chooses the JAR directory for packaged runs or the working directory for development runs. */
    //@@author naieel04
    static Path resolvePath(Path codeLocation, Path workingDirectory) {
        Path location = codeLocation.toAbsolutePath().normalize();
        Path folder = location.getFileName().toString().endsWith(".jar")
                ? location.getParent() : workingDirectory.toAbsolutePath().normalize();
        return folder.resolve(FILE_NAME);
    }

    /**
     * Returns the absolute save path for startup diagnostics.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Loads valid rows, using a fully valid backup only when the main file cannot be recovered.
     * Damaged files remain untouched until the user makes a change and a save is attempted.
     */
    @Override
    public LoadResult load() {
        logger.fine("Loading ledger from " + path);
        initialized = true;
        knownClean = false;
        preserveOriginal = false;
        savingAllowed = true;
        setupRequired = false;
        //@@author naieel04
        preservedSources.clear();
        try {
            LedgerCodec.DecodeResult decoded = read(path);
            if (decoded == null) {
                if (!Files.exists(backupPath())) {
                    return createSetupPendingFile(new ArrayList<>());
                }
                //@@author naieel04
                return recoverBackup(false, new ArrayList<>());
            }
            return handleMainFile(decoded);
        } catch (CharacterCodingException e) {
            preserveOriginal = true;
            return recoverBackup(true, new ArrayList<>(List.of("Save file contains invalid UTF-8.")));
        } catch (IOException e) {
            return readFailure(path);
        }
    }

    /** Applies the recovery policy for a decoded main file. */
    //@@author naieel04
    private LoadResult handleMainFile(LedgerCodec.DecodeResult decoded) {
        assert decoded != null : "Decoded main file result must not be null";
        //@@author naieel04
        List<String> warnings = new ArrayList<>(decoded.warnings());
        switch (decoded.status()) {
        case SETUP_PENDING:
            setupRequired = true;
            logger.info("First-run setup is incomplete; prompting for an opening balance");
            return result(LedgerSnapshot.empty(), warnings);
        //@@author naieel04
        case CLEAN:
            knownClean = true;
            logger.fine("Loaded clean ledger file");
            return result(decoded.snapshot(), warnings);
        case PARTIAL:
            preserveOriginal = true;
            logger.warning("Loaded partially recoverable ledger file");
            warnings.add("Recovered valid rows from " + path + ". Balances may be incomplete. "
                    + "The original file will be preserved before any save.");
            return result(decoded.snapshot(), warnings);
        case UNSUPPORTED:
            savingAllowed = false;
            logger.warning("Storage format is unsupported; saving disabled");
            warnings.add("Unsupported save format at " + path + ". Saving is disabled for this session.");
            return result(LedgerSnapshot.empty(), warnings);
        case UNRECOVERABLE:
            preserveOriginal = true;
            return recoverBackup(true, warnings);
        default:
            throw new IllegalStateException("Unknown storage decode status");
        }
    }

    /** Uses a clean backup when available and otherwise returns an empty, safely unsaved state. */
    //@@author naieel04
    private LoadResult recoverBackup(boolean damagedMain, List<String> warnings) {
        try {
            LedgerCodec.DecodeResult backup = read(backupPath());
            if (backup != null && backup.status() == LedgerCodec.Status.CLEAN) {
                logger.warning("Recovered ledger from backup file");
                warnings.add("Recovered financial state from backup: " + backupPath()
                        + ". Files will remain unchanged until a state-changing command is used.");
                return result(backup.snapshot(), warnings);
            }
            if (backup != null && backup.status() == LedgerCodec.Status.SETUP_PENDING) {
                logger.info("Resuming setup from pending backup state");
                warnings.add("First-run setup was not completed. Enter and confirm an opening balance to continue.");
                return createSetupPendingFile(warnings);
            }
            //@@author naieel04
            if (backup != null && backup.status() == LedgerCodec.Status.UNSUPPORTED) {
                savingAllowed = false;
                warnings.add("Unsupported backup format at " + backupPath()
                        + ". Starting an unsaved session. Saving is disabled to protect incompatible data.");
                return result(LedgerSnapshot.empty(), warnings);
            }
            if (backup != null) {
                warnings.add("The backup is not fully valid and was not used: " + backupPath());
            }
        } catch (CharacterCodingException e) {
            warnings.add("The backup contains invalid UTF-8 and was not used: " + backupPath());
        } catch (IOException e) {
            return readFailure(backupPath());
        }
        if (!damagedMain) {
            logger.warning("No usable main ledger exists; creating a pending setup file");
            return createSetupPendingFile(warnings);
        }
        //@@author naieel04
        warnings.add("The save file is corrupted and no saved state could be recovered. "
                + "FinNUS is starting with an empty ledger. To inspect or repair the original, "
                + "exit without changing transactions, opening balance or rates. Open "
                + path + ", repair it, then restart FinNUS.");
        return result(LedgerSnapshot.empty(), warnings);
    }

    /** Disables saving after a source cannot be read, protecting data that may be inaccessible. */
    //@@author naieel04
    private LoadResult readFailure(Path source) {
        savingAllowed = false;
        //@@author nkyjStar
        logger.log(java.util.logging.Level.WARNING, "Unable to read storage file " + source);
        //@@author naieel04
        return result(LedgerSnapshot.empty(), List.of("Unable to read " + source
                + ". Starting an unsaved session. Saving is disabled to protect data that could not be loaded."));
    }

    /** Logs recovery warnings and packages the result with the current saving policy. */
    //@@author naieel04
    private LoadResult result(LedgerSnapshot snapshot, List<String> warnings) {
        for (String warning : warnings) {
            //@@author nkyjStar
            logger.warning(warning);
        }
        //@@author naieel04
        return new LoadResult(snapshot, warnings, savingAllowed, setupRequired);
    }

    //@@author lololol8484
    /** Creates the main file in a pending state before asking the user to complete setup. */
    private LoadResult createSetupPendingFile(List<String> warnings) {
        setupRequired = true;
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path) && preserveOriginal) {
                preserveFile(path);
            }
            temporary = Files.createTempFile(path.getParent(), ".finnus-", ".tmp");
            Files.writeString(temporary, codec.encodeSetupPending(), StandardCharsets.UTF_8);
            replacement.replace(temporary, path);
            assert Files.exists(path) : "Pending setup replacement must create the ledger file";
            preserveOriginal = false;
            //@@author nkyjStar
            logger.info("Created pending setup file at " + path);
            //@@author lololol8484
            return result(LedgerSnapshot.empty(), warnings);
        } catch (IOException e) {
            savingAllowed = false;
            //@@author nkyjStar
            logger.log(java.util.logging.Level.SEVERE, "Could not create pending setup file at " + path, e);
            //@@author lololol8484
            warnings.add("Unable to initialize " + path + ". Setup can continue, but the opening balance "
                    + "cannot be saved.");
            return result(LedgerSnapshot.empty(), warnings);
        } finally {
            if (temporary != null) {
                removeTemporary(temporary);
            }
        }
    }
    //@@author naieel04

    /** Decodes a storage file, returning {@code null} when it does not exist. */
    //@@author naieel04
    private LedgerCodec.DecodeResult read(Path source) throws IOException {
        try {
            return codec.decode(Files.readString(source, StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    /**
     * Writes a complete snapshot without truncating the main file or its backup.
     *
     * @throws IOException If writing, backup rotation or atomic replacement fails.
     */
    @Override
    public void save(LedgerSnapshot snapshot) throws IOException {
        assert snapshot != null : "Cannot save a null ledger snapshot";
        //@@author nkyjStar
        logger.fine("Saving ledger to " + path);
        //@@author naieel04
        if (!initialized) {
            load();
        }
        if (!savingAllowed) {
            throw new IOException("Saving is disabled because the source could not be loaded safely");
        }
        Files.createDirectories(path.getParent());
        if (preserveOriginal) {
            preserveFile(path);
        }
        Path temporary = Files.createTempFile(path.getParent(), ".finnus-", ".tmp");
        try {
            Files.writeString(temporary, codec.encode(snapshot), StandardCharsets.UTF_8);
            if (knownClean && Files.exists(path)) {
                rotateBackup();
            }
            replacement.replace(temporary, path);
            knownClean = true;
            preserveOriginal = false;
            //@@author nkyjStar
            logger.fine("Saved ledger snapshot");
        } catch (IOException e) {
            //@@author nkyjStar
            logger.log(java.util.logging.Level.WARNING, "Failed to save ledger to " + path, e);
            //@@author naieel04
            throw e;
        //@@author naieel04
        } finally {
            removeTemporary(temporary);
        }
    }

    /** Copies the current clean ledger into the backup using atomic replacement. */
    //@@author naieel04
    private void rotateBackup() throws IOException {
        protectDamagedBackup();
        Path temporary = Files.createTempFile(path.getParent(), ".finnus-", ".tmp");
        try {
            Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
            replacement.replace(temporary, backupPath());
        } finally {
            removeTemporary(temporary);
        }
    }

    /** Preserves a damaged backup before rotating another ledger over it. */
    //@@author naieel04
    private void protectDamagedBackup() throws IOException {
        try {
            LedgerCodec.DecodeResult backup = read(backupPath());
            if (backup != null && backup.status() != LedgerCodec.Status.CLEAN) {
                preserveFile(backupPath());
            }
        } catch (CharacterCodingException e) {
            preserveFile(backupPath());
        }
    }

    /**
     * Copies damaged bytes to a unique recovery file before any replacement is permitted.
     */
    private void preserveFile(Path source) throws IOException {
        if (preservedSources.contains(source)) {
            return;
        }
        Path destination = Files.createTempFile(path.getParent(), source.getFileName() + ".corrupt-", "");
        Path temporary = null;
        try {
            temporary = Files.createTempFile(path.getParent(), ".finnus-", ".tmp");
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            replacement.replace(temporary, destination);
            preservedSources.add(source);
            logger.warning("Preserved damaged storage file at " + destination);
        } catch (IOException e) {
            removeTemporary(destination);
            throw e;
        } finally {
            if (temporary != null) {
                removeTemporary(temporary);
            }
        }
    }

    /** Returns the sibling path used for the previous clean ledger. */
    //@@author naieel04
    private Path backupPath() {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    /** Removes a temporary file and logs cleanup failures without hiding the main result. */
    //@@author naieel04
    private void removeTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            logger.log(java.util.logging.Level.FINE, "Could not remove temporary storage file " + temporary, e);
        }
    }

    //@@author naieel04
    /**
     * Provides a narrow failure-injection seam without depending on platform permission behavior.
     */
    @FunctionalInterface
    interface AtomicReplacement {
        /** Atomically replaces the target with the source when supported by the filesystem. */
        void replace(Path source, Path target) throws IOException;
    }
}
//@@author
