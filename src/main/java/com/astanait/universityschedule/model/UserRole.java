package com.astanait.universityschedule.model;

import org.springframework.security.core.GrantedAuthority;

public enum UserRole implements GrantedAuthority {
    ADMIN("Администратор"),
    TEACHER("Преподаватель"),
    STUDENT("Студент");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getAuthority() {
        // Spring Security ожидает роли в формате "ROLE_ИМЯ_РОЛИ"
        return "ROLE_" + this.name();
    }
}