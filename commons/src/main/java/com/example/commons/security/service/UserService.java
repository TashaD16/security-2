package com.example.commons.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

/**
 * Сервис для управления пользователями и их правами доступа.
 * Общий для всех модулей.
 */
@Slf4j
@Service
public class UserService {

    // В реальном приложении это должно быть в базе данных
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserService() {
        // Инициализация тестовых пользователей
        initializeTestUsers();
    }

    private void initializeTestUsers() {
        // Пользователь с правами администратора
        users.put("user1", new User(
            "user1",
            "admin",
            Arrays.asList("ADMIN", "USER"),
            Arrays.asList("READ_DECLARATION", "WRITE_DECLARATION", "APPROVE_DECLARATION",
                         "READ_WARE", "WRITE_WARE", "MANAGE_INVENTORY", "ADMIN")
        ));

        // Пользователь с ограниченными правами (только чтение)
        users.put("user2", new User(
            "user2",
            "operator",
            Arrays.asList("USER"),
            Arrays.asList("READ_DECLARATION", "READ_WARE")
        ));

        // Пользователь только для модуля A (декларации)
        users.put("user3", new User(
            "user3",
            "moduleA_user",
            Arrays.asList("USER"),
            Arrays.asList("READ_DECLARATION", "WRITE_DECLARATION")
        ));

        // Пользователь только для модуля B (товары)
        users.put("user4", new User(
            "user4",
            "moduleB_user",
            Arrays.asList("USER"),
            Arrays.asList("READ_WARE", "WRITE_WARE", "MANAGE_INVENTORY")
        ));
    }

    public Optional<User> findByUserId(String userId) {
        User user = users.get(userId);
        if (user != null) {
            log.debug("Found user: {} with roles: {} and authorities: {}", 
                    user.getUsername(), user.getRoles(), user.getAuthorities());
            return Optional.of(user);
        }
        log.warn("User not found: {}", userId);
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    /**
     * Представляет пользователя системы
     */
    public static class User {
        private final String userId;
        private final String username;
        private final List<String> roles;
        private final List<String> authorities;

        public User(String userId, String username, List<String> roles, List<String> authorities) {
            this.userId = userId;
            this.username = username;
            this.roles = roles;
            this.authorities = authorities;
        }

        public String getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public List<String> getRoles() {
            return roles;
        }

        public List<String> getAuthorities() {
            return authorities;
        }
    }
}
