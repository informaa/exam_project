package com.astanait.universityschedule.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration // говорит Spring, что это класс с настройками
@EnableWebSecurity // включает безопасность на основе Spring Security.
public class SecurityConfig {

    @Bean // регистрирует метод как компонент Spring
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean //  метод создаёт цепочку фильтров безопасности
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/schedule/new", "/schedule/edit/**", "/schedule/save", "/schedule/update", "/schedule/delete/**").hasRole("ADMIN")
                        .requestMatchers("/schedule/api/week-boundaries").authenticated() // или hasAnyRole("ADMIN", "USER")
                        .requestMatchers("/schedule").authenticated() // или hasAnyRole("ADMIN", "USER")
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login") // Указывает Spring Security, что /login - это путь к кастомной странице входа
                        .loginProcessingUrl("/login") // URL, на который отправляется форма (POST)
                        .defaultSuccessUrl("/schedule", true)
                        .failureUrl("/login?error=true")
                        .permitAll() // Разрешает доступ к самой loginPage и loginProcessingUrl
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }
}
