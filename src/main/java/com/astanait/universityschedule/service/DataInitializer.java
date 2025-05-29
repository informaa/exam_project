package com.astanait.universityschedule.service;

import com.astanait.universityschedule.model.*;
import com.astanait.universityschedule.repository.GroupRepository;
import com.astanait.universityschedule.repository.RoomRepository;
import com.astanait.universityschedule.repository.SubjectRepository;
import com.astanait.universityschedule.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Компонент для инициализации начальных данных при запуске приложения
@Component
public class DataInitializer implements CommandLineRunner {

    // Репозиторий для работы с пользователями
    private final UserRepository userRepository;
    // Кодировщик паролей
    private final PasswordEncoder passwordEncoder;
    // Репозиторий для работы с группами
    private final GroupRepository groupRepository;
    // Репозиторий для работы с аудиториями
    private final RoomRepository roomRepository;
    // Репозиторий для работы с предметами
    private final SubjectRepository subjectRepository;

    // Конструктор для внедрения зависимостей
    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           GroupRepository groupRepository, RoomRepository roomRepository,
                           SubjectRepository subjectRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.groupRepository = groupRepository;
        this.roomRepository = roomRepository;
        this.subjectRepository = subjectRepository;
    }

    // Метод, выполняемый при запуске приложения
    @Override
    public void run(String... args) throws Exception {
        // Проверка и создание администратора, если он не существует
        if (userRepository.findByUsername("admin").isEmpty()) {
            AppUser admin = new AppUser(
                    "admin", // Логин
                    passwordEncoder.encode("admin"), // Закодированный пароль
                    UserRole.ADMIN, // Роль
                    "Главный Администратор" // Полное имя
            );
            userRepository.save(admin); // Сохранение администратора
            System.out.println("Создан пользователь: " + admin.getFullName() + " (Логин: " + admin.getUsername() + ") с ролью " + admin.getRole());
        }

        // Массив названий тестовых групп
        String[] groupNames = {"ВТ23A-типо", "ВТ23В-типо", "ВТ23Г-типо"};
        // Создание тестовых групп, если они не существуют
        for (String name : groupNames) {
            if (groupRepository.findByName(name).isEmpty()) {
                Group group = new Group(name);
                groupRepository.save(group);
                System.out.println("Создана тестовая группа: " + group.getName());
            }
        }

        // Поиск или создание дефолтной группы
        Optional<Group> defaultGroupOptional = groupRepository.findByName("ВТ23Б-типо");
        Group defaultGroup;
        if (defaultGroupOptional.isEmpty()) {
            defaultGroup = new Group("ВТ23Б-типо");
            groupRepository.save(defaultGroup);
            System.out.println("Создана дефолтная группа: " + defaultGroup.getName());
        } else {
            defaultGroup = defaultGroupOptional.get();
        }

        // Поиск или создание первого тестового преподавателя
        AppUser teacher1 = userRepository.findByUsername("test1").orElseGet(() -> {
            AppUser newTeacher = new AppUser(
                    "test1",
                    passwordEncoder.encode("pass1"),
                    UserRole.TEACHER,
                    "test1"
            );
            userRepository.save(newTeacher);
            System.out.println("Создан преподаватель: " + newTeacher.getFullName());
            return newTeacher;
        });

        // Поиск или создание второго тестового преподавателя
        AppUser teacher2 = userRepository.findByUsername("test2").orElseGet(() -> {
            AppUser newTeacher = new AppUser(
                    "test2",
                    passwordEncoder.encode("pass2"),
                    UserRole.TEACHER,
                    "test2"
            );
            userRepository.save(newTeacher);
            System.out.println("Создан преподаватель: " + newTeacher.getFullName());
            return newTeacher;
        });

        // Поиск или создание третьего тестового преподавателя
        AppUser teacher3 = userRepository.findByUsername("test3").orElseGet(() -> {
            AppUser newTeacher = new AppUser(
                    "test3",
                    passwordEncoder.encode("test3"),
                    UserRole.TEACHER,
                    "test3"
            );
            userRepository.save(newTeacher);
            System.out.println("Создан преподаватель: " + newTeacher.getFullName());
            return newTeacher;
        });

        // Проверка и создание первого тестового студента, если он не существует
        if (userRepository.findByUsername("student1").isEmpty()) {
            AppUser student1 = new AppUser(
                    "student1",
                    passwordEncoder.encode("password"),
                    UserRole.STUDENT,
                    "Студент1",
                    defaultGroup // Присвоение дефолтной группы
            );
            userRepository.save(student1);
            System.out.println("Создан студент: " + student1.getFullName() + " (Логин: " + student1.getUsername() + ") с ролью " + student1.getRole());
        }
        // Проверка и создание второго тестового студента, если он не существует
        if (userRepository.findByUsername("student2").isEmpty()) {
            AppUser student2 = new AppUser(
                    "student2",
                    passwordEncoder.encode("password"),
                    UserRole.STUDENT,
                    "Студент2",
                    defaultGroup // Присвоение дефолтной группы
            );
            userRepository.save(student2);
            System.out.println("Создан студент: " + student2.getFullName() + " (Логин: " + student2.getUsername() + ") с ролью " + student2.getRole());
        }

        // Массив номеров тестовых аудиторий
        String[] roomNumbers = {"409", "410", "411"};
        // Массив вместимостей тестовых аудиторий
        int[] capacities = {30, 30, 30};
        // Создание тестовых аудиторий, если они не существуют
        for (int i = 0; i < roomNumbers.length; i++) {
            if (roomRepository.findByRoomNumber(roomNumbers[i]).isEmpty()) {
                Room room = new Room(roomNumbers[i], capacities[i]);
                roomRepository.save(room);
                System.out.println("Создана тестовая аудитория: " + room.getRoomNumber());
            }
        }

        // Проверка и создание первого тестового предмета, если он не существует
        if (subjectRepository.findByName("Продвинутое программирование на языке JAVA").isEmpty()) {
            Subject subject1 = new Subject("Продвинутое программирование на языке JAVA", 10, teacher1);
            subjectRepository.save(subject1);
            System.out.println("Создан предмет: " + subject1.getName());
        }
        // Проверка и создание второго тестового предмета, если он не существует
        if (subjectRepository.findByName("Анализ данных").isEmpty()) {
            Subject subject2 = new Subject("Анализ данных", 10, teacher2);
            subjectRepository.save(subject2);
            System.out.println("Создан предмет: " + subject2.getName());
        }
        // Проверка и создание третьего тестового предмета, если он не существует
        if (subjectRepository.findByName("Разработка мобильных приложений").isEmpty()) {
            Subject subject3 = new Subject("Разработка мобильных приложений", 10, teacher3);
            subjectRepository.save(subject3);
            System.out.println("Создан предмет: " + subject3.getName());
        }
    }
}