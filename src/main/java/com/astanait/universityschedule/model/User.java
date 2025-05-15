package com.astanait.universityschedule.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

// Аннотации над классом
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor

// Данные об одном пользователе
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    // поле указывает, активирован ли аккаунт пользователя
    @Column(nullable = false)
    private boolean enabled = true;

    // Связь с Role (Many-to-Many)
    @ManyToMany(fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public User(String username, String password, boolean enabled) {
        this.username = username;
        this.password = password;
        this.enabled = enabled;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

}
