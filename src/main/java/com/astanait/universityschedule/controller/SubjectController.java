package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.model.Subject;
import com.astanait.universityschedule.model.UserRole;
import com.astanait.universityschedule.repository.UserRepository;
import com.astanait.universityschedule.service.SubjectService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Контроллер для управления учебными предметами, доступен только администраторам
@Controller
@RequestMapping("/admin/subjects") // Базовый URL для всех методов контроллера
@PreAuthorize("hasRole('ADMIN')") // Доступ только для пользователей с ролью ADMIN
public class SubjectController {

    private final SubjectService subjectService; // Сервис для работы с предметами
    private final UserRepository userRepository; // Репозиторий для работы с пользователями

    // Конструктор для внедрения зависимостей
    public SubjectController(SubjectService subjectService, UserRepository userRepository) {
        this.subjectService = subjectService;
        this.userRepository = userRepository;
    }

    // Отображает список всех учебных предметов
    @GetMapping
    public String listSubjects(Model model) {
        model.addAttribute("subjects", subjectService.getAllSubjects()); // Добавление списка предметов в модель
        return "admin/subject-list"; // Возвращает имя представления для отображения списка
    }

    // Отображает форму для создания нового учебного предмета
    @GetMapping("/new")
    public String newSubjectForm(Model model) {
        model.addAttribute("subject", new Subject()); // Добавление нового объекта Subject в модель
        // Загрузка списка пользователей с ролью TEACHER для выбора преподавателя
        List<AppUser> teachers = userRepository.findByRole(UserRole.TEACHER);
        model.addAttribute("teachers", teachers); // Добавление списка преподавателей в модель
        return "admin/subject-form"; // Возвращает имя представления для отображения формы
    }

    // Сохраняет новый или обновленный учебный предмет
    @PostMapping("/save")
    public String saveSubject(@ModelAttribute("subject") Subject subject) {
        subjectService.saveSubject(subject); // Сохранение предмета через сервис
        return "redirect:/admin/subjects"; // Перенаправление на список предметов
    }

    // Отображает форму для редактирования существующего учебного предмета
    @GetMapping("/edit/{id}")
    public String editSubjectForm(@PathVariable Long id, Model model) {
        // Поиск предмета по ID или выброс исключения, если не найден
        Subject subject = subjectService.getSubjectById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid subject Id:" + id));
        model.addAttribute("subject", subject); // Добавление найденного предмета в модель
        // Загрузка списка пользователей с ролью TEACHER для выбора преподавателя
        List<AppUser> teachers = userRepository.findByRole(UserRole.TEACHER);
        model.addAttribute("teachers", teachers); // Добавление списка преподавателей в модель
        return "admin/subject-form"; // Возвращает имя представления для отображения формы
    }

    // Удаляет учебный предмет по его ID
    @GetMapping("/delete/{id}")
    public String deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id); // Удаление предмета через сервис
        return "redirect:/admin/subjects"; // Перенаправление на список предметов
    }
}