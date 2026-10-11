package com.inventory.util;

import com.inventory.service.DataStore;
import java.nio.file.Path;

/** Where the application's data is persisted. Implemented by JdbcStorage (SQLite) and FileStorage (legacy file). */
public interface Storage {
    /** @return the saved data, or null if nothing has been saved yet. */
    DataStore load();
    void save(DataStore store);
    Path backup();
}
