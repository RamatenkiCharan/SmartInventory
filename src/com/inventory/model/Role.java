package com.inventory.model;

import java.util.EnumSet;
import java.util.Set;

/** Role-based access control: each role owns an EnumSet of permissions. */
public enum Role {
    ADMIN(EnumSet.allOf(Permission.class)),
    MANAGER(EnumSet.complementOf(EnumSet.of(Permission.USER_MANAGE, Permission.PRODUCT_DELETE))),
    STAFF(EnumSet.of(Permission.VIEW, Permission.SELL));

    private final Set<Permission> permissions;
    Role(Set<Permission> permissions) { this.permissions = permissions; }
    public boolean can(Permission p) { return permissions.contains(p); }
}
