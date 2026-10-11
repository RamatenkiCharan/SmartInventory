package com.inventory.service;

/** Command pattern: an operation that knows how to apply and reverse itself. */
public interface Command {
    void execute();
    void undo();
    String description();
}
