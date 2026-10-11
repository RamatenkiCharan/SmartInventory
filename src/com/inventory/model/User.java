package com.inventory.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String username;
    private String fullName;
    private String passwordHash;
    private Role role;
    private boolean active = true;

    public User(String username, String fullName, String password, Role role) {
        this.username = username.trim().toLowerCase();
        this.fullName = fullName.trim();
        this.passwordHash = hash(password);
        this.role = role;
    }

    /** Rebuilds a stored user from an already-hashed password (used when loading from the database). */
    public static User restore(String username, String fullName, String passwordHash, Role role, boolean active) {
        User u = new User(username, fullName, "x", role);
        u.passwordHash = passwordHash; u.active = active;
        return u;
    }
    public String getPasswordHash() { return passwordHash; }

    public static String hash(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public boolean checkPassword(String raw) { return passwordHash.equals(hash(raw)); }
    public void setPassword(String raw)      { this.passwordHash = hash(raw); }
    public String getUsername()  { return username; }
    public String getFullName()  { return fullName; }
    public Role getRole()        { return role; }
    public void setRole(Role r)  { this.role = r; }
    public boolean isActive()    { return active; }
    public void setActive(boolean a) { this.active = a; }
}
