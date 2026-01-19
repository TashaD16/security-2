package com.example.moduleB.service;

import com.example.moduleB.dto.WareDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис для работы с товарами
 */
@Slf4j
@Service
public class WareService {

    private final ConcurrentHashMap<String, WareDto> wares = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public WareService() {
        // Инициализация тестовых данных
        initializeTestData();
    }

    private void initializeTestData() {
        WareDto ware1 = new WareDto();
        ware1.setId("1");
        ware1.setName("Product 1");
        ware1.setCode("PRD-001");
        ware1.setPrice(new BigDecimal("100.00"));
        ware1.setDescription("Test product 1");
        ware1.setQuantity(10);
        wares.put("1", ware1);

        WareDto ware2 = new WareDto();
        ware2.setId("2");
        ware2.setName("Product 2");
        ware2.setCode("PRD-002");
        ware2.setPrice(new BigDecimal("200.00"));
        ware2.setDescription("Test product 2");
        ware2.setQuantity(20);
        wares.put("2", ware2);
    }

    public List<WareDto> getAllWares() {
        log.debug("Getting all wares");
        return new ArrayList<>(wares.values());
    }

    public Optional<WareDto> getWare(String id) {
        log.debug("Getting ware with id: {}", id);
        return Optional.ofNullable(wares.get(id));
    }

    public WareDto createWare(WareDto ware) {
        String id = String.valueOf(idGenerator.getAndIncrement());
        ware.setId(id);
        wares.put(id, ware);
        log.info("Created ware with id: {}", id);
        return ware;
    }

    public Optional<WareDto> updateWare(String id, WareDto ware) {
        if (wares.containsKey(id)) {
            ware.setId(id);
            wares.put(id, ware);
            log.info("Updated ware with id: {}", id);
            return Optional.of(ware);
        }
        log.warn("Ware with id {} not found for update", id);
        return Optional.empty();
    }

    public boolean deleteWare(String id) {
        WareDto removed = wares.remove(id);
        if (removed != null) {
            log.info("Deleted ware with id: {}", id);
            return true;
        }
        log.warn("Ware with id {} not found for deletion", id);
        return false;
    }
}
