package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.service.CustomUserDetailsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Контроллер для обработки запросов к главной странице
@Controller
public class HomeController {

    // Сервис для работы с данными пользователя
    private final CustomUserDetailsService userDetailsService;

    // Конструктор для внедрения зависимости CustomUserDetailsService
    public HomeController(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    // Обрабатывает GET-запросы к корневому URL ("/")
    @GetMapping("/")
    public String home(@AuthenticationPrincipal UserDetails currentUser) {
        // currentUser будет не null, так как URL защищен и требует аутентификации
        if (currentUser != null) {
            // Загружаем полные данные пользователя (AppUser) по его имени пользователя (username)
            AppUser appUser = (AppUser) userDetailsService.loadUserByUsername(currentUser.getUsername());

            // Перенаправление в зависимости от роли пользователя
            if (appUser.getRole().name().equals("ADMIN")) {
                return "redirect:/admin"; // Перенаправление на страницу администратора
            } else if (appUser.getRole().name().equals("STUDENT")) {
                return "redirect:/schedule"; // Перенаправление на страницу расписания для студента
            }
            // Если роль не ADMIN и не STUDENT, перенаправляем на страницу входа с ошибкой
            return "redirect:/login?error=role_not_found";
        }
        // Этот блок теоретически не должен выполняться, так как пользователь аутентифицирован
        // Возврат на страницу входа для непредвиденных случаев
        return "redirect:/login";
    }
}