package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.model.Group;
import com.astanait.universityschedule.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    List<AppUser> findByRole(UserRole role);
    List<AppUser> findByStudentGroup(Group group);
}