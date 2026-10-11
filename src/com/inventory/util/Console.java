package com.inventory.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/** Validated console input. Re-prompts until the input is valid. */
public class Console {
    private final Scanner sc = new Scanner(System.in);

    public String optional(String label) {
        System.out.print(label + ": ");
        if (!sc.hasNextLine()) throw new InputClosedException();
        return sc.nextLine().trim();
    }

    public String required(String label) {
        while (true) {
            String s = optional(label);
            if (!s.isEmpty()) return s;
            System.out.println("  This field is required.");
        }
    }

    public int integer(String label) {
        while (true) {
            try { return Integer.parseInt(required(label)); }
            catch (NumberFormatException e) { System.out.println("  Please enter a whole number."); }
        }
    }

    public double decimal(String label) {
        while (true) {
            try { return Double.parseDouble(required(label)); }
            catch (NumberFormatException e) { System.out.println("  Please enter a number."); }
        }
    }

    /** Decimal with a default used when the user just presses Enter. */
    public double decimal(String label, double defaultValue) {
        while (true) {
            String s = optional(label);
            if (s.isEmpty()) return defaultValue;
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) { System.out.println("  Please enter a number."); }
        }
    }

    public LocalDate date(String label, boolean allowBlank) {
        while (true) {
            String s = optional(label);
            if (s.isEmpty() && allowBlank) return null;
            try { return LocalDate.parse(s); }
            catch (DateTimeParseException e) { System.out.println("  Use format yyyy-MM-dd (e.g. 2026-12-31)."); }
        }
    }

    public boolean confirm(String label) {
        return optional(label + " (y/n)").equalsIgnoreCase("y");
    }
}
