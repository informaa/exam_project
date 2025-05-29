package com.astanait.universityschedule.service;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.model.Subject;
import com.astanait.universityschedule.model.UserRole;
import com.astanait.universityschedule.model.Group;
import com.astanait.universityschedule.repository.SubjectRepository;
import com.astanait.universityschedule.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// Сервис для управления пользователями
@Service
public class UserService {

    private final UserRepository userRepository; // Репозиторий для работы с пользователями
    private final PasswordEncoder passwordEncoder; // Кодировщик паролей
    private final GroupService groupService; // Сервис для работы с группами
    private final SubjectRepository subjectRepository; // Репозиторий для работы с предметами

    // Конструктор для внедрения зависимостей
    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       GroupService groupService,
                       SubjectRepository subjectRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.groupService = groupService;
        this.subjectRepository = subjectRepository;
    }

    // Получение списка всех пользователей
    public List<AppUser> getAllUsers() {
        return userRepository.findAll();
    }

    // Получение пользователя по ID
    public Optional<AppUser> getUserById(Long id) {
        return userRepository.findById(id);
    }

    // Сохранение пользователя (нового или обновление существующего)
    @Transactional
    public AppUser saveUser(AppUser user) {
        // Шифрование пароля, если он новый или изменен и еще не зашифрован
        if (user.getId() == null || (user.getPassword() != null && !user.getPassword().isEmpty() && !user.getPassword().startsWith("$2a$"))) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(user);
    }

    // Удаление пользователя по ID
    @Transactional
    public void deleteUser(Long id) {
        Optional<AppUser> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            AppUser userToDelete = userOptional.get();

            // Если удаляемый пользователь - преподаватель, отвязываем его от всех предметов
            if (userToDelete.getRole() == UserRole.TEACHER) {
                List<Subject> subjectsTaught = subjectRepository.findByTeacher(userToDelete);
                for (Subject subject : subjectsTaught) {
                    subject.setTeacher(null); // Отвязываем преподавателя от предмета
                    subjectRepository.save(subject); // Сохраняем изменения в предмете
                }
            }

            // Удаляем самого пользователя
            userRepository.deleteById(id);
        } else {
            // Логирование или выброс исключения, если пользователь не найден
            System.err.println("Пользователь с ID " + id + " не найден для удаления.");
            // throw new UserNotFoundException("Пользователь с ID " + id + " не найден.");
        }
    }

    @Transactional
    public void removeGroupFromStudents(Group group) {
        List<AppUser> studentsInGroup = userRepository.findByStudentGroup(group);
        for (AppUser student : studentsInGroup) {
            student.setStudentGroup(null);
            userRepository.save(student);
        }
    }

    // Обновление пароля пользователя
    public void updateUserPassword(AppUser user, String newPassword) {
        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        }
    }

    // Поиск пользователя по имени пользователя (логину)
    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}