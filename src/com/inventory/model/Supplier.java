package com.inventory.model;

import java.io.Serializable;

public class Supplier implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private String name, phone, email, city;

    public Supplier(String id, String name, String phone, String email, String city) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Supplier ID is required.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Supplier name is required.");
        this.id = id.trim().toUpperCase();
        this.name = name.trim(); this.phone = phone; this.email = email; this.city = city;
    }
    public String getId()    { return id; }
    public String getName()  { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getCity()  { return city; }
    public void setName(String v)  { if (v == null || v.isBlank()) throw new IllegalArgumentException("Name required."); name = v.trim(); }
    public void setPhone(String v) { phone = v; }
    public void setEmail(String v) { email = v; }
    public void setCity(String v)  { city = v; }
}
