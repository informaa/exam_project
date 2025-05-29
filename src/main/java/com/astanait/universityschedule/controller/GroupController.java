package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.model.Group;
import com.astanait.universityschedule.service.GroupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// Контроллер для управления группами в административной панели
@Controller
@RequestMapping("/admin/groups") // Базовый URL для всех запросов, связанных с группами
@PreAuthorize("hasRole('ADMIN')") // Доступ только для пользователей с ролью ADMIN
public class GroupController {

    private final GroupService groupService; // Сервис для работы с группами

    // Конструктор для внедрения зависимости GroupService
    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    // Отображает страницу со списком всех групп
    @GetMapping
    public String listGroups(Model model) {
        model.addAttribute("groups", groupService.getAllGroups()); // Добавляет список групп в модель
        return "admin/group-list"; // Возвращает имя HTML-шаблона для отображения списка
    }

    // Отображает форму для создания новой группы
    @GetMapping("/new")
    public String newGroupForm(Model model) {
        model.addAttribute("group", new Group()); // Добавляет новый пустой объект Group в модель
        return "admin/group-form"; // Возвращает имя HTML-шаблона для формы создания
    }

    // Обрабатывает запрос на сохранение новой или обновление существующей группы
    @PostMapping("/save")
    public String saveGroup(@ModelAttribute("group") Group group) {
        // Здесь может быть добавлена валидация данных группы перед сохранением
        groupService.saveGroup(group); // Сохраняет группу через сервис
        return "redirect:/admin/groups"; // Перенаправляет пользователя на страницу со списком групп
    }

    // Отображает форму для редактирования существующей группы
    @GetMapping("/edit/{id}")
    public String editGroupForm(@PathVariable Long id, Model model) {
        Group group = groupService.getGroupById(id) // Получает группу по ID
                .orElseThrow(() -> new IllegalArgumentException("Invalid group Id:" + id)); // Выбрасывает исключение, если группа не найдена
        model.addAttribute("group", group); // Добавляет найденную группу в модель
        return "admin/group-form"; // Возвращает имя HTML-шаблона для формы редактирования
    }

    // Обрабатывает запрос на удаление группы по ID
    @GetMapping("/delete/{id}")
    public String deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id); // Удаляет группу через сервис
        return "redirect:/admin/groups"; // Перенаправляет пользователя на страницу со списком групп
    }
}