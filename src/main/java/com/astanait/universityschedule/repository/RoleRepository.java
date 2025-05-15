package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Аннотация над интерфейсом
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // метод , который ищет роль по её имени
    Optional<Role> findByName(String name);
}
