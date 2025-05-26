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

// Объявление класса и инъекция зависимостей
@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public DataLoader(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Метод run() и логирование
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("Загрузка начальных данных...");

        // Создание ролей
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            log.info("Создание роли ROLE_ADMIN");
            return roleRepository.save(new Role("ROLE_ADMIN"));
        });

        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            log.info("Создание роли ROLE_USER");
            return roleRepository.save(new Role("ROLE_USER"));
        });

        // Создание/обновление пользователя-админа
        String adminUsername = "admin";
        String adminPassword = "adminpassword";
        // проверяем: есть ли в БД пользователь с таким логином ("admin")
        userRepository.findByUsername(adminUsername).ifPresentOrElse(
                existingAdmin -> {
                    log.info("Пользователь '{}' уже существует. Проверка/обновление данных...", adminUsername);
                    boolean needsSave = false;
                    // Если у пользователя нет роли `adminRole` или их несколько — заменяем на одну `Set.of(adminRole)` и помечаем `needsSave = true`
                    if (!existingAdmin.getRoles().contains(adminRole) || existingAdmin.getRoles().size() != 1) {
                        existingAdmin.setRoles(Set.of(adminRole));
                        needsSave = true;
                        log.info("Роли для '{}' обновлены.", adminUsername);
                    }

                    // Сравниваем текущий пароль в БД с тем, который мы хотим установить
                    // Если они разные — шифруем новый пароль и обновляем запись
                    if (!passwordEncoder.matches(adminPassword, existingAdmin.getPassword())) {
                        existingAdmin.setPassword(passwordEncoder.encode(adminPassword));
                        needsSave = true;
                        log.info("Пароль для '{}' обновлен.", adminUsername);
                    }

                    // У администратора не должно быть группы
                    // Если у существующего админа группа указана — очищаем её
                    if (existingAdmin.getGroupName() != null) {
                        existingAdmin.setGroupName(null);
                        needsSave = true;
                        log.info("Группа для '{}' очищена.", adminUsername);
                    }
                    // Если хоть одно поле изменилось — сохраняем обновлённого пользователя в БД
                    if (needsSave) {
                        userRepository.save(existingAdmin);
                    }
                },
                 /* Создаём нового пользователя с ролью администратора
                 Пароль шифруем
                 Группу оставляем null, потому что у админов её нет
                 Сохраняем в БД и логируем */
                () -> {
                    User adminUser = new User(adminUsername, passwordEncoder.encode(adminPassword), true, null);
                    adminUser.setRoles(Set.of(adminRole));
                    userRepository.save(adminUser);
                    log.info("Создан пользователь '{}' с ролью ROLE_ADMIN. Пароль: '{}'", adminUsername, adminPassword);
                }
        );

        // Создание/обновление пользователя-студента (studentA)
        String studentAUsername = "studentA";
        String studentAPassword = "passA";
        String studentAGroup = "ВТ-23А";
        userRepository.findByUsername(studentAUsername).ifPresentOrElse(
                existingStudent -> {
                    log.info("Пользователь '{}' уже существует. Проверка/обновление данных...", studentAUsername);
                    boolean needsSave = false;
                    if (!existingStudent.getRoles().contains(userRole) || existingStudent.getRoles().size() != 1) {
                        existingStudent.setRoles(Set.of(userRole));
                        needsSave = true;
                        log.info("Роли для '{}' обновлены.", studentAUsername);
                    }
                    if (!passwordEncoder.matches(studentAPassword, existingStudent.getPassword())) {
                        existingStudent.setPassword(passwordEncoder.encode(studentAPassword));
                        needsSave = true;
                        log.info("Пароль для '{}' обновлен.", studentAUsername);
                    }
                    if (existingStudent.getGroupName() == null || !existingStudent.getGroupName().equals(studentAGroup)) {
                        existingStudent.setGroupName(studentAGroup);
                        needsSave = true;
                        log.info("Группа для '{}' обновлена на '{}'.", studentAUsername, studentAGroup);
                    }
                    if (needsSave) {
                        userRepository.save(existingStudent);
                    }
                },
                () -> {
                    User studentA = new User(studentAUsername, passwordEncoder.encode(studentAPassword), true, studentAGroup);
                    studentA.setRoles(Set.of(userRole));
                    userRepository.save(studentA);
                    log.info("Создан пользователь '{}' (группа {}) с ролью ROLE_USER. Пароль: '{}'", studentAUsername, studentAGroup, studentAPassword);
                }
        );

        String studentBUsername = "studentB";
        String studentBPassword = "passB";
        String studentBGroup = "ВТ-23Б";
        userRepository.findByUsername(studentBUsername).ifPresentOrElse(
                existingStudent -> {
                    log.info("Пользователь '{}' уже существует. Проверка/обновление данных...", studentBUsername);
                    boolean needsSave = false;
                    if (!existingStudent.getRoles().contains(userRole) || existingStudent.getRoles().size() != 1) {
                        existingStudent.setRoles(Set.of(userRole));
                        needsSave = true;
                        log.info("Роли для '{}' обновлены.", studentBUsername);
                    }
                    if (!passwordEncoder.matches(studentBPassword, existingStudent.getPassword())) {
                        existingStudent.setPassword(passwordEncoder.encode(studentBPassword));
                        needsSave = true;
                        log.info("Пароль для '{}' обновлен.", studentBUsername);
                    }
                    if (existingStudent.getGroupName() == null || !existingStudent.getGroupName().equals(studentBGroup)) {
                        existingStudent.setGroupName(studentBGroup);
                        needsSave = true;
                        log.info("Группа для '{}' обновлена на '{}'.", studentBUsername, studentBGroup);
                    }
                    if (needsSave) {
                        userRepository.save(existingStudent);
                    }
                },
                () -> {
                    User studentB = new User(studentBUsername, passwordEncoder.encode(studentBPassword), true, studentBGroup);
                    studentB.setRoles(Set.of(userRole));
                    userRepository.save(studentB);
                    log.info("Создан пользователь '{}' (группа {}) с ролью ROLE_USER. Пароль: '{}'", studentBUsername, studentBGroup, studentBPassword);
                }
        );

        log.info("Загрузка начальных данных завершена.");
    }
}
