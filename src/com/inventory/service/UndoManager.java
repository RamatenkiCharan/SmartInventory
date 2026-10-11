package com.inventory.service;

import java.util.ArrayDeque;
import java.util.Deque;

/** Two stacks (ArrayDeque) implementing undo / redo. Session-only, capped at 50 entries. */
public class UndoManager {
    private static final int MAX = 50;
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    public void execute(Command c) {
        c.execute();
        undoStack.push(c);
        if (undoStack.size() > MAX) undoStack.removeLast();
        redoStack.clear();
    }

    public String undo() {
        Command c = undoStack.peek();
        if (c == null) throw new IllegalStateException("Nothing to undo.");
        c.undo();                     // if this throws, the command stays on the stack
        undoStack.pop();
        redoStack.push(c);
        return c.description();
    }

    public String redo() {
        Command c = redoStack.peek();
        if (c == null) throw new IllegalStateException("Nothing to redo.");
        c.execute();
        redoStack.pop();
        undoStack.push(c);
        return c.description();
    }

    public int undoCount() { return undoStack.size(); }
    public int redoCount() { return redoStack.size(); }
    public String peekUndo() { Command c = undoStack.peek(); return c == null ? "-" : c.description(); }
    public String peekRedo() { Command c = redoStack.peek(); return c == null ? "-" : c.description(); }
}
