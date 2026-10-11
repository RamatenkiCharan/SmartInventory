package com.inventory.util;

import com.inventory.service.DataStore;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Persists the whole DataStore using Java object serialization. */
public class FileStorage implements Storage {
    private final Path file;

    public FileStorage(Path file) { this.file = file; }

    @Override public DataStore load() {
        if (!Files.exists(file)) return null;
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return (DataStore) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.out.println("Warning: could not read saved data (" + e.getMessage() + "). Starting fresh.");
            return null;
        }
    }

    @Override public void save(DataStore store) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeObject(store);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new UncheckedIOException("Could not save data", e); }
    }

    @Override public Path backup() {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path dir = file.toAbsolutePath().getParent().resolve("backups");
            Files.createDirectories(dir);
            Path target = dir.resolve("inventory-" + stamp + ".dat");
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) { throw new UncheckedIOException("Backup failed", e); }
    }
}
