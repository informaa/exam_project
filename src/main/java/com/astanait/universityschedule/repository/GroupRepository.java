package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByName(String name); // Метод для поиска группы по имени
}