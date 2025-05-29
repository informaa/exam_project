package com.astanait.universityschedule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration // Обозначает, что класс является конфигурационным
@EnableWebSecurity // Включает поддержку Spring Security
public class SecurityConfig {

    @Bean // Определяет бин для цепочки фильтров безопасности
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize // Настройка авторизации HTTP-запросов
                        // Разрешение доступа к главной странице, странице входа и странице ошибок для всех
                        .requestMatchers("/", "/login", "/error").permitAll()
                        // Доступ к генерации расписания только для пользователей с ролью ADMIN
                        .requestMatchers("/schedule/generate").hasRole("ADMIN")
                        // Доступ к просмотру расписания для всех аутентифицированных пользователей
                        .requestMatchers("/schedule").authenticated()
                        // Доступ ко всем URL, начинающимся с /admin/, только для пользователей с ролью ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Все остальные запросы требуют аутентификации
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form // Настройка формы входа
                        .loginPage("/login") // Указание кастомной страницы входа
                        .defaultSuccessUrl("/", true) // URL по умолчанию после успешного входа
                        .permitAll() // Разрешение доступа к странице входа для всех
                )
                .logout(logout -> logout // Настройка выхода из системы
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout")) // URL для выхода
                        .logoutSuccessUrl("/login?logout") // URL после успешного выхода
                        .permitAll() // Разрешение доступа к функционалу выхода для всех
                );

        return http.build(); // Построение и возврат цепочки фильтров безопасности
    }

    @Bean // Определяет бин для кодировщика паролей
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Использование BCrypt для хеширования паролей
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico");
    }
}