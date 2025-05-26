package com.astanait.universityschedule.service;

import com.astanait.universityschedule.model.User;
import com.astanait.universityschedule.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
// реализуем стандартный интерфейс Spring Security, который требует метод loadUserByUsername
public class UserDetailsServiceImpl implements UserDetailsService {

    // Внедряем `UserRepository` через конструктор для получения данных пользователя из БД
    private final UserRepository userRepository;

    // Метод вызывается при входе пользователя, принимает `username` и возвращает объект `UserDetails`
    @Autowired
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    // Ищем пользователя по имени: если не найден — выбрасываем `UsernameNotFoundException`
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с именем '" + username + "' не найден"));

        // Получаем роли пользователя и преобразуем их в GrantedAuthority для Spring Security.
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());

         /* Создаём объект `User` из Spring Security с данными:
         логин, пароль, активность,
         состояние аккаунта (срок действия, блокировка),
         роли (`authorities`). */
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                authorities
        );
    }
}
