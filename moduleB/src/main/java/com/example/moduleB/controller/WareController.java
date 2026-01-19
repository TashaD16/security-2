package com.example.moduleB.controller;

import com.example.moduleB.dto.WareDto;
import com.example.moduleB.service.WareService;
import com.example.commons.security.annotation.RequireReadWare;
import com.example.commons.security.annotation.RequireWriteWare;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Контроллер для работы с товарами
 * Использует кастомные аннотации для проверки прав доступа.
 * Вся авторизация обрабатывается в gateway модуле на основе этих аннотаций.
 */
@RestController
@RequestMapping("/api/moduleB/wares")
@RequiredArgsConstructor
public class WareController {

    private final WareService wareService;

    @GetMapping
    @RequireReadWare
    public ResponseEntity<List<WareDto>> getAllWares() {
        return ResponseEntity.ok(wareService.getAllWares());
    }

    @GetMapping("/{wareId}")
    @RequireReadWare
    public ResponseEntity<WareDto> getWare(@PathVariable String wareId) {
        return wareService.getWare(wareId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @RequireWriteWare
    public ResponseEntity<WareDto> createWare(@Valid @RequestBody WareDto ware) {
        WareDto created = wareService.createWare(ware);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{wareId}")
    @RequireWriteWare
    public ResponseEntity<WareDto> updateWare(
            @PathVariable String wareId,
            @Valid @RequestBody WareDto ware) {
        return wareService.updateWare(wareId, ware)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{wareId}")
    @RequireWriteWare
    public ResponseEntity<Void> deleteWare(@PathVariable String wareId) {
        if (wareService.deleteWare(wareId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
