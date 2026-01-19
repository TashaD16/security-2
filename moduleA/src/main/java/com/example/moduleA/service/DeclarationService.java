package com.example.moduleA.service;

import com.example.moduleA.dto.DeclarationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис для работы с декларациями
 */
@Slf4j
@Service
public class DeclarationService {

    private final ConcurrentHashMap<String, DeclarationDto> declarations = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public DeclarationService() {
        // Инициализация тестовых данных
        initializeTestData();
    }

    private void initializeTestData() {
        DeclarationDto declaration1 = new DeclarationDto();
        declaration1.setId("1");
        declaration1.setNumber("DEC-001");
        declaration1.setType("IMPORT");
        declaration1.setDate(LocalDateTime.now());
        declaration1.setDescription("Test declaration 1");
        declarations.put("1", declaration1);

        DeclarationDto declaration2 = new DeclarationDto();
        declaration2.setId("2");
        declaration2.setNumber("DEC-002");
        declaration2.setType("EXPORT");
        declaration2.setDate(LocalDateTime.now());
        declaration2.setDescription("Test declaration 2");
        declarations.put("2", declaration2);
    }

    public List<DeclarationDto> getAllDeclarations() {
        log.debug("Getting all declarations");
        return new ArrayList<>(declarations.values());
    }

    public Optional<DeclarationDto> getDeclaration(String id) {
        log.debug("Getting declaration with id: {}", id);
        return Optional.ofNullable(declarations.get(id));
    }

    public DeclarationDto createDeclaration(DeclarationDto declaration) {
        String id = String.valueOf(idGenerator.getAndIncrement());
        declaration.setId(id);
        declarations.put(id, declaration);
        log.info("Created declaration with id: {}", id);
        return declaration;
    }

    public Optional<DeclarationDto> updateDeclaration(String id, DeclarationDto declaration) {
        if (declarations.containsKey(id)) {
            declaration.setId(id);
            declarations.put(id, declaration);
            log.info("Updated declaration with id: {}", id);
            return Optional.of(declaration);
        }
        log.warn("Declaration with id {} not found for update", id);
        return Optional.empty();
    }

    public boolean deleteDeclaration(String id) {
        DeclarationDto removed = declarations.remove(id);
        if (removed != null) {
            log.info("Deleted declaration with id: {}", id);
            return true;
        }
        log.warn("Declaration with id {} not found for deletion", id);
        return false;
    }
}
