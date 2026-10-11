package com.inventory.util;

/** Thrown when stdin is closed (Ctrl+D / end of piped input) so the app can exit cleanly. */
public class InputClosedException extends RuntimeException {
    public InputClosedException() { super("Input closed"); }
}
