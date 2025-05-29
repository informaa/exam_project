package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.model.Room;
import com.astanait.universityschedule.service.RoomService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller // Определяет класс как контроллер Spring MVC
@RequestMapping("/admin/rooms") // Базовый URL для всех методов этого контроллера
@PreAuthorize("hasRole('ADMIN')") // Требует роль ADMIN для доступа ко всем методам контроллера
public class RoomController {

    private final RoomService roomService; // Сервис для работы с аудиториями

    // Конструктор для внедрения зависимости RoomService
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // Обрабатывает GET-запросы на /admin/rooms для отображения списка всех аудиторий
    @GetMapping
    public String listRooms(Model model) {
        model.addAttribute("rooms", roomService.getAllRooms()); // Добавляет список аудиторий в модель
        return "admin/room-list"; // Возвращает имя HTML-шаблона для отображения списка
    }

    // Обрабатывает GET-запросы на /admin/rooms/new для отображения формы создания новой аудитории
    @GetMapping("/new")
    public String newRoomForm(Model model) {
        model.addAttribute("room", new Room()); // Добавляет новый объект Room в модель
        return "admin/room-form"; // Возвращает имя HTML-шаблона для формы
    }

    // Обрабатывает POST-запросы на /admin/rooms/save для сохранения новой или обновленной аудитории
    @PostMapping("/save")
    public String saveRoom(@ModelAttribute("room") Room room) {
        // Можно добавить валидацию данных аудитории перед сохранением
        roomService.saveRoom(room); // Сохраняет аудиторию через сервис
        return "redirect:/admin/rooms"; // Перенаправляет на страницу списка аудиторий
    }

    // Обрабатывает GET-запросы на /admin/rooms/edit/{id} для отображения формы редактирования аудитории
    @GetMapping("/edit/{id}")
    public String editRoomForm(@PathVariable Long id, Model model) {
        Room room = roomService.getRoomById(id) // Получает аудиторию по ID
                .orElseThrow(() -> new IllegalArgumentException("Invalid room Id:" + id)); // Выбрасывает исключение, если аудитория не найдена
        model.addAttribute("room", room); // Добавляет найденную аудиторию в модель
        return "admin/room-form"; // Возвращает имя HTML-шаблона для формы (тот же, что и для создания)
    }

    // Обрабатывает GET-запросы на /admin/rooms/delete/{id} для удаления аудитории
    @GetMapping("/delete/{id}")
    public String deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id); // Удаляет аудиторию по ID через сервис
        return "redirect:/admin/rooms"; // Перенаправляет на страницу списка аудиторий
    }
}