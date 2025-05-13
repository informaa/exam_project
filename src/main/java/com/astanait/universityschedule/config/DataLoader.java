package com.astanait.universityschedule.config;

import com.astanait.universityschedule.model.Role;
import com.astanait.universityschedule.model.User;
import com.astanait.universityschedule.repository.RoleRepository;
import com.astanait.universityschedule.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;


    // Spring сам передаст эти объекты в конструктор
    @Autowired
    public DataLoader(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("Загрузка начальных данных...");

        // Проверяем, есть ли роль "ROLE_ADMIN" в БД
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            log.info("Создание роли ROLE_ADMIN");
            return roleRepository.save(new Role("ROLE_ADMIN"));
        });

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            log.info("Создание роли ROLE_USER");
            return roleRepository.save(new Role("ROLE_USER"));
        });

        if (userRepository.findByUsername("admin").isEmpty()) {
            User adminUser = new User("admin", passwordEncoder.encode("adminpassword"), true);
            adminUser.setRoles(Set.of(adminRole));
            userRepository.save(adminUser);
            log.info("Создан пользователь 'admin' с ролью ROLE_ADMIN. Пароль: 'adminpassword'");
        } else {
            log.info("Пользователь 'admin' уже существует.");
        }

        if (userRepository.findByUsername("user").isEmpty()) {
            User regularUser = new User("user", passwordEncoder.encode("userpassword"), true);
            regularUser.setRoles(Set.of(userRole));
            userRepository.save(regularUser);
            log.info("Создан пользователь 'user' с ролью ROLE_USER. Пароль: 'userpassword'");
        } else {
            log.info("Пользователь 'user' уже существует.");
        }

        log.info("Загрузка начальных данных завершена.");
    }
}
