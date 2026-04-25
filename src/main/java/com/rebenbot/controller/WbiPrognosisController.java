package com.rebenbot.controller;

import com.rebenbot.model.WbiPrognosis;
import com.rebenbot.service.WbiPrognosisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/wbi")
@CrossOrigin(origins = "*")
@Slf4j
public class WbiPrognosisController {

    private final WbiPrognosisService wbiPrognosisService;

    public WbiPrognosisController(WbiPrognosisService wbiPrognosisService) {
        this.wbiPrognosisService = wbiPrognosisService;
    }

    /**
     * Get latest WBI prognosis for a disease.
     * @param disease "peronospora" or "oidium"
     */
    @GetMapping("/prognosis/latest")
    public ResponseEntity<?> getLatestPrognosis(@RequestParam String disease) {
        log.debug("Fetching latest {} prognosis from WBI Freiburg", disease);
        return wbiPrognosisService.getLatestPrognosis(disease)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get WBI prognosis history for a disease over a date range.
     */
    @GetMapping("/prognosis/history")
    public ResponseEntity<List<WbiPrognosis>> getPrognosisHistory(
            @RequestParam String disease,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("Fetching {} prognosis history from {} to {}", disease, startDate, endDate);
        List<WbiPrognosis> history = wbiPrognosisService.getPrognosisHistory(disease, startDate, endDate);
        return ResponseEntity.ok(history);
    }

    /**
     * Manually trigger prognosis refresh (for testing).
     */
    @PostMapping("/prognosis/refresh")
    public ResponseEntity<String> refreshPrognosis(@RequestParam String disease) {
        try {
            if ("peronospora".equalsIgnoreCase(disease)) {
                wbiPrognosisService.refreshPeronosporePrognosis();
            } else if ("oidium".equalsIgnoreCase(disease)) {
                wbiPrognosisService.refreshOidiumPrognosis();
            } else {
                return ResponseEntity.badRequest().body("Unknown disease: " + disease);
            }
            return ResponseEntity.ok("Refresh initiated for " + disease);
        } catch (Exception e) {
            log.error("Error refreshing prognosis: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
