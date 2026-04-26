package com.rebenbot.controller;

import com.rebenbot.repository.FungicideProductRepository;
import com.rebenbot.service.FungicideDataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints for synchronising fungicide data against the BVL PSM-API.
 *
 * <p><b>BVL PSM-API sync</b><br>
 * {@code POST /api/v1/admin/sync/bvl-api} — queries the BVL PSM-API
 * ({@code https://psm-api.bvl.bund.de/ords/psm/api-v1/}) for each product in the local
 * database, matching by product name, and records the German registration number
 * (Zulassungsnummer) and expiry date.  Runs automatically on a monthly cron.
 *
 * <p><b>Sync status</b><br>
 * {@code GET /api/v1/admin/sync/status} — returns the timestamp and result of the last
 * BVL sync, plus product database statistics.
 */
@RestController
@RequestMapping("/v1/admin/sync")
@CrossOrigin(origins = "*")
@Slf4j
public class DataSyncController {

    private final FungicideDataSyncService syncService;
    private final FungicideProductRepository productRepository;

    public DataSyncController(FungicideDataSyncService syncService,
                              FungicideProductRepository productRepository) {
        this.syncService = syncService;
        this.productRepository = productRepository;
    }

    /**
     * Triggers an immediate synchronisation against the BVL PSM-API.
     * Searches each local product by name in BVL and records the registration number
     * and expiry date.  Also runs automatically on a monthly cron.
     */
    @PostMapping("/bvl-api")
    public ResponseEntity<Map<String, Object>> triggerBvlApiSync() {
        log.info("Manual BVL API sync triggered via admin endpoint");
        String result = syncService.triggerBvlApiSync();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.startsWith("ERROR") ? "error" : "success");
        body.put("message", result);
        return result.startsWith("ERROR")
                ? ResponseEntity.internalServerError().body(body)
                : ResponseEntity.ok(body);
    }

    /**
     * Returns the current sync status and product database statistics.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> syncStatus = syncService.getSyncStatus();

        long totalProducts = productRepository.count();
        long withBvlData = productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getBvlApprovedInGermany()))
                .count();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sync", syncStatus);
        body.put("products", Map.of(
                "total", totalProducts,
                "withBvlVerification", withBvlData
        ));
        return ResponseEntity.ok(body);
    }
}
