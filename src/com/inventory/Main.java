package com.inventory;

import com.inventory.db.Database;
import com.inventory.db.JdbcStorage;
import com.inventory.service.DataStore;
import com.inventory.service.SampleData;
import com.inventory.gui.AppContext;
import com.inventory.gui.LoginFrame;
import com.inventory.gui.Theme;
import com.inventory.ui.ConsoleApp;
import com.inventory.util.FileStorage;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point.  Data is stored in a database through JDBC (SQLite file data/inventory.db by default).
 * Options:  --reset   wipe the database and reload the demo data
 *           --console use the console menus instead of the window
 * Use  -Ddb.url=jdbc:sqlite:other.db  to point at a different database file.
 */
public class Main {
    public static void main(String[] args) {
        boolean console = false, reset = false;
        for (String a : args) { if (a.equals("--console")) console = true; if (a.equals("--reset")) reset = true; }

        JdbcStorage storage;
        try {
            storage = new JdbcStorage();
        } catch (RuntimeException e) {
            String msg = "Could not open the database:\n" + e.getMessage()
                    + "\n\nMake sure lib/sqlite-jdbc.jar is next to the application (see README).";
            System.err.println(msg);
            if (!console && !java.awt.GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null, msg, "Smart Inventory", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        System.out.println("Database: " + Database.url());
        if (reset) storage.clearAll();

        DataStore store = storage.load();
        if (store == null) {
            Path legacy = Paths.get("data", "inventory.dat");           // data from the pre-database version
            if (!reset && Files.exists(legacy)) store = new FileStorage(legacy).load();
            if (store != null) System.out.println("Imported existing data from " + legacy + " into the database.");
            else { store = SampleData.create(); System.out.println("First run: demo data created."); }
            storage.save(store);
        }
        if (console) { new ConsoleApp(store, storage).run(); return; }
        final AppContext ctx = new AppContext(store, storage);
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new LoginFrame(ctx).setVisible(true);
        });
    }
}
