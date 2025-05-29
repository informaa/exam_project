package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByName(String name);
    List<Subject> findByTeacher(AppUser teacher);
}