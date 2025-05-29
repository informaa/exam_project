package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.model.UserRole;
import com.astanait.universityschedule.service.GroupService;
import com.astanait.universityschedule.service.SubjectService;
import com.astanait.universityschedule.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller // Определяет класс как контроллер Spring MVC
@RequestMapping("/admin") // Сопоставляет HTTP-запросы с путем "/admin" с этим контроллером
public class AdminController {

    private final UserService userService; // Сервис для управления пользователями
    private final GroupService groupService; // Сервис для управления группами
    private final SubjectService subjectService; // Сервис для управления предметами
    private final PasswordEncoder passwordEncoder; // Кодировщик паролей

    @Autowired // Автоматически внедряет зависимости
    public AdminController(UserService userService, GroupService groupService, SubjectService subjectService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.groupService = groupService;
        this.subjectService = subjectService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping // Обрабатывает GET-запросы к "/admin"
    public String adminPanel(Model model) {
        // Отображает главную страницу административной панели
        return "admin/admin-panel";
    }

    @GetMapping("/users") // Обрабатывает GET-запросы к "/admin/users"
    public String manageUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers()); // Добавляет список всех пользователей в модель
        return "admin/users"; // Возвращает имя представления для отображения страницы управления пользователями
    }

    @GetMapping("/users/new") // Обрабатывает GET-запросы к "/admin/users/new"
    public String createUserForm(Model model) {
        model.addAttribute("user", new AppUser()); // Добавляет новый объект AppUser для связывания с формой
        model.addAttribute("groups", groupService.getAllGroups()); // Добавляет список всех групп
        model.addAttribute("roles", UserRole.values()); // Добавляет все возможные роли пользователей
        return "admin/user-form"; // Возвращает имя представления для отображения формы создания пользователя
    }

    @PostMapping("/users/save") // Обрабатывает POST-запросы к "/admin/users/save"
    public String saveUser(@ModelAttribute AppUser user, @RequestParam(value = "groupId", required = false) Long groupId, Model model) {
        // Проверка на существование пользователя с таким же логином при создании нового
        if (user.getId() == null && userService.findByUsername(user.getUsername()).isPresent()) {
            model.addAttribute("errorMessage", "Пользователь с таким логином уже существует."); // Сообщение об ошибке
            model.addAttribute("user", user); // Возврат введенных данных в форму
            model.addAttribute("groups", groupService.getAllGroups());
            model.addAttribute("roles", UserRole.values());
            return "admin/user-form"; // Вернуться на форму создания/редактирования
        }

        // Установка пароля: кодирование нового или сохранение существующего
        if (user.getId() == null || (user.getPassword() != null && !user.getPassword().isEmpty())) {
            user.setPassword(passwordEncoder.encode(user.getPassword())); // Кодирование нового пароля
        } else {
            // Если это редактирование и пароль не меняется, оставляем старый
            if (user.getId() != null) {
                AppUser existingUser = userService.getUserById(user.getId())
                        .orElseThrow(() -> new RuntimeException("Пользователь не найден при редактировании!"));
                user.setPassword(existingUser.getPassword()); // Используем существующий пароль
            }
        }

        // Установка группы для студента
        if (groupId != null) {
            groupService.getGroupById(groupId).ifPresent(user::setStudentGroup); // Установить группу, если ID предоставлен
        } else if (user.getRole() != UserRole.STUDENT) {
            user.setStudentGroup(null); // Убрать группу, если роль не студент
        }

        userService.saveUser(user); // Сохранение пользователя
        return "redirect:/admin/users"; // Перенаправление на страницу управления пользователями
    }

    @GetMapping("/users/edit/{id}") // Обрабатывает GET-запросы к "/admin/users/edit/{id}"
    public String editUserForm(@PathVariable Long id, Model model) {
        Optional<AppUser> userOptional = userService.getUserById(id); // Поиск пользователя по ID
        if (userOptional.isPresent()) {
            model.addAttribute("user", userOptional.get()); // Добавление найденного пользователя в модель
            model.addAttribute("groups", groupService.getAllGroups());
            model.addAttribute("roles", UserRole.values());
            return "admin/user-form"; // Возврат формы для редактирования
        }
        return "redirect:/admin/users"; // Перенаправление, если пользователь не найден
    }

    @PostMapping("/users/delete/{id}") // Обрабатывает POST-запросы к "/admin/users/delete/{id}"
    public String deleteUser(@PathVariable Long id, Model model) {
        userService.deleteUser(id); // Удаление пользователя по ID
        return "redirect:/admin/users"; // Перенаправление на страницу управления пользователями
    }

}