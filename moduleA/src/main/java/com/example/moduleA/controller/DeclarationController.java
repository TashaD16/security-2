package com.example.moduleA.controller;

import com.example.moduleA.dto.DeclarationDto;
import com.example.moduleA.service.DeclarationService;
import com.example.commons.security.annotation.RequireReadDeclaration;
import com.example.commons.security.annotation.RequireWriteDeclaration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Контроллер для работы с декларациями
 * Использует кастомные аннотации для проверки прав доступа.
 * Вся авторизация обрабатывается в gateway модуле на основе этих аннотаций.
 */
@RestController
@RequestMapping("/api/moduleA/declarations")
@RequiredArgsConstructor
public class DeclarationController {

    private final DeclarationService declarationService;

    @GetMapping
    @RequireReadDeclaration
    public ResponseEntity<List<DeclarationDto>> getAllDeclarations() {
        return ResponseEntity.ok(declarationService.getAllDeclarations());
    }

    @GetMapping("/{declarationId}")
    @RequireReadDeclaration
    public ResponseEntity<DeclarationDto> getDeclaration(@PathVariable String declarationId) {
        return declarationService.getDeclaration(declarationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @RequireWriteDeclaration
    public ResponseEntity<DeclarationDto> createDeclaration(@Valid @RequestBody DeclarationDto declaration) {
        DeclarationDto created = declarationService.createDeclaration(declaration);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{declarationId}")
    @RequireWriteDeclaration
    public ResponseEntity<DeclarationDto> updateDeclaration(
            @PathVariable String declarationId,
            @Valid @RequestBody DeclarationDto declaration) {
        return declarationService.updateDeclaration(declarationId, declaration)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{declarationId}")
    @RequireWriteDeclaration
    public ResponseEntity<Void> deleteDeclaration(@PathVariable String declarationId) {
        if (declarationService.deleteDeclaration(declarationId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
