package com.inventory.service;

import com.inventory.exception.*;
import com.inventory.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Authentication, role checks and user administration. */
public class AuthService {
    private final DataStore store;
    private User current;

    public AuthService(DataStore store) { this.store = store; }

    public User login(String username, String password) {
        User u = store.users.get(username.trim().toLowerCase());
        if (u == null || !u.isActive() || !u.checkPassword(password))
            throw new AccessDeniedException("Invalid username or password.");
        current = u;
        return u;
    }

    public void logout()            { current = null; }
    public User current()           { return current; }
    public String currentUsername() { return current == null ? "system" : current.getUsername(); }

    public void require(Permission p) {
        if (current == null) throw new AccessDeniedException("Please log in first.");
        if (!current.getRole().can(p))
            throw new AccessDeniedException("Access denied: your role (" + current.getRole() + ") lacks permission " + p + ".");
    }

    // ---- user administration (ADMIN) ----
    public List<User> listUsers() {
        require(Permission.USER_MANAGE);
        List<User> list = new ArrayList<>(store.users.values());
        list.sort(Comparator.comparing(User::getUsername));
        return list;
    }

    public void addUser(String username, String fullName, String password, Role role) {
        require(Permission.USER_MANAGE);
        String key = username.trim().toLowerCase();
        if (key.isEmpty()) throw new IllegalArgumentException("Username required.");
        if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (store.users.containsKey(key)) throw new DuplicateException("Username already exists: " + key);
        store.users.put(key, new User(key, fullName, password, role));
    }

    public void resetPassword(String username, String newPassword) {
        require(Permission.USER_MANAGE);
        if (newPassword.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        find(username).setPassword(newPassword);
    }

    public void setRole(String username, Role role) {
        require(Permission.USER_MANAGE);
        User u = find(username);
        if (u.getRole() == Role.ADMIN && role != Role.ADMIN && adminCount() <= 1)
            throw new IllegalStateException("Cannot demote the last active admin.");
        u.setRole(role);
    }

    public void setActive(String username, boolean active) {
        require(Permission.USER_MANAGE);
        User u = find(username);
        if (u == current && !active) throw new IllegalStateException("You cannot deactivate yourself.");
        if (!active && u.getRole() == Role.ADMIN && adminCount() <= 1)
            throw new IllegalStateException("Cannot deactivate the last active admin.");
        u.setActive(active);
    }

    public void deleteUser(String username) {
        require(Permission.USER_MANAGE);
        User u = find(username);
        if (u == current) throw new IllegalStateException("You cannot delete yourself.");
        if (u.getRole() == Role.ADMIN && adminCount() <= 1) throw new IllegalStateException("Cannot delete the last admin.");
        store.users.remove(u.getUsername());
    }

    public void changeOwnPassword(String oldPassword, String newPassword) {
        if (current == null) throw new AccessDeniedException("Please log in first.");
        if (!current.checkPassword(oldPassword)) throw new AccessDeniedException("Current password is incorrect.");
        if (newPassword.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        current.setPassword(newPassword);
    }

    private User find(String username) {
        User u = store.users.get(username.trim().toLowerCase());
        if (u == null) throw new NotFoundException("User", username);
        return u;
    }

    private long adminCount() {
        return store.users.values().stream().filter(x -> x.getRole() == Role.ADMIN && x.isActive()).count();
    }
}
