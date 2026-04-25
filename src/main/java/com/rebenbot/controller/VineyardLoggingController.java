package com.rebenbot.controller;

import com.rebenbot.controller.dto.SprayApplicationRequest;
import com.rebenbot.controller.dto.VineyardDiaryEntryRequest;
import com.rebenbot.model.VineyardLogEntry;
import com.rebenbot.service.VineyardLoggingService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/vineyard-logs")
@CrossOrigin(origins = "*")
@Slf4j
public class VineyardLoggingController {

    private final VineyardLoggingService loggingService;

    public VineyardLoggingController(VineyardLoggingService loggingService) {
        this.loggingService = loggingService;
    }

    // ============ SPRAY ENDPOINTS ============

    /**
     * Record a spray application.
     * 
     * Request body:
     * {
     *   "vineyardId": 1,
     *   "fungicideId": 5,
     *   "diseaseId": 1,
     *   "applicationDate": "2026-04-16T14:30:00",
     *   "growthStageBbch": "75",
     *   "temperatureC": 18.5,
     *   "humidityPercent": 65.0,
     *   "windSpeedMsec": 2.5,
     *   "amountFungicideAppliedLiters": 45.5,
     *   "notes": "Applied in afternoon, clear conditions"
     * }
     */
    @PostMapping("/record-spray")
    public ResponseEntity<?> recordSpray(@Valid @RequestBody SprayApplicationRequest request) {
        try {
            VineyardLogEntry logEntry = loggingService.recordSpray(
                    request.getVineyardId(), 
                    request.getFungicideId(), 
                    request.getDiseaseId(), 
                    request.getApplicationDate(), 
                    request.getGrowthStageBbch(), 
                    request.getTemperatureC(), 
                    request.getHumidityPercent(), 
                    request.getWindSpeedMsec(), 
                    request.getAmountFungicideAppliedLiters(),
                    request.getNotes());

            // Assess effectiveness after recording
            loggingService.assessSprayEffectiveness(logEntry);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Spray application recorded",
                    "spray", sprayToMap(logEntry)
            ));
        } catch (Exception e) {
            log.error("Error recording spray:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get spray history for a vineyard.
     * Optional query params: ?lastDays=30
     */
    @GetMapping("/spray-history/{vineyardId}")
    public ResponseEntity<?> getSprayHistory(
            @PathVariable Long vineyardId,
            @RequestParam(required = false) Integer lastDays) {
        try {
            List<VineyardLogEntry> sprays = loggingService.getSprayHistory(vineyardId, lastDays);
            List<Map<String, Object>> sprayMaps = sprays.stream()
                    .map(this::sprayToMap)
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "vineyardId", vineyardId,
                    "sprayCount", sprays.size(),
                    "lastDays", lastDays != null ? lastDays : "all",
                    "sprays", sprayMaps
            ));
        } catch (Exception e) {
            log.error("Error fetching spray history:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get recent sprays (last 7 days) for dashboard display.
     */
    @GetMapping("/recent-sprays/{vineyardId}")
    public ResponseEntity<?> getRecentSprays(@PathVariable Long vineyardId) {
        try {
            List<VineyardLogEntry> sprays = loggingService.getRecentSprays(vineyardId);
            List<Map<String, Object>> sprayMaps = sprays.stream()
                    .map(this::sprayToMap)
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "recentSprayCount", sprays.size(),
                    "sprays", sprayMaps
            ));
        } catch (Exception e) {
            log.error("Error fetching recent sprays:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get spray frequency analysis (how often each fungicide is used).
     */
    @GetMapping("/spray-frequency/{vineyardId}")
    public ResponseEntity<?> getSprayFrequencyAnalysis(
            @PathVariable Long vineyardId,
            @RequestParam(required = false) Integer lastDays) {
        try {
            Map<String, Integer> frequency = loggingService.getSprayFrequencyAnalysis(vineyardId, lastDays);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "period", lastDays != null ? lastDays + " days" : "all time",
                    "frequencyByFungicide", frequency
            ));
        } catch (Exception e) {
            log.error("Error fetching frequency analysis:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    // ============ DIARY ENTRY ENDPOINTS ============

    /**
     * Create a new diary entry.
     *
     * Request body:
     * {
     *   "vineyardId": 1,
     *   "entryDate": "2026-04-25T14:30:00",
     *   "title": "Observation: Early budbreak",
     *   "description": "Noticed early budbreak on north side due to warmer exposure",
     *   "entryType": "OBSERVATION",
     *   "growthStageBbch": "09",
     *   "tags": "spring,budbreak,phenology"
     * }
     */
    @PostMapping("/create-entry")
    public ResponseEntity<?> createDiaryEntry(@Valid @RequestBody VineyardDiaryEntryRequest request) {
        try {
            VineyardLogEntry.DiaryEntryType entryType = VineyardLogEntry.DiaryEntryType.valueOf(request.getEntryType());
            
            VineyardLogEntry entry = loggingService.createDiaryEntry(
                    request.getVineyardId(),
                    request.getEntryDate(),
                    request.getTitle(),
                    request.getDescription(),
                    entryType,
                    request.getGrowthStageBbch(),
                    request.getTags());

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Diary entry created",
                    "entry", diaryToMap(entry)
            ));
        } catch (IllegalArgumentException e) {
            log.error("Invalid entry type provided", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Invalid entry type. Valid types: " + Arrays.toString(VineyardLogEntry.DiaryEntryType.values())
            ));
        } catch (Exception e) {
            log.error("Error creating diary entry:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get all diary entries for a vineyard.
     * Ordered by date (newest first).
     */
    @GetMapping("/entries/{vineyardId}")
    public ResponseEntity<?> getDiaryEntries(@PathVariable Long vineyardId) {
        try {
            List<VineyardLogEntry> entries = loggingService.getDiaryEntries(vineyardId);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "count", entries.size(),
                    "entries", entries.stream().map(this::diaryToMap).collect(Collectors.toList())
            ));
        } catch (Exception e) {
            log.error("Error retrieving diary entries:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get diary entries within a date range.
     * Query params: ?startDate=2026-04-01T00:00:00&endDate=2026-04-30T23:59:59
     */
    @GetMapping("/entries/{vineyardId}/range")
    public ResponseEntity<?> getDiaryEntriesByRange(
            @PathVariable Long vineyardId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            List<VineyardLogEntry> entries = loggingService.getDiaryEntriesByDateRange(vineyardId, startDate, endDate);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "count", entries.size(),
                    "entries", entries.stream().map(this::diaryToMap).collect(Collectors.toList())
            ));
        } catch (Exception e) {
            log.error("Error retrieving diary entries by date range:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get diary entries of a specific type.
     * Query param: ?type=OBSERVATION or WEATHER, PEST_DISEASE, MAINTENANCE, HARVEST, OTHER
     */
    @GetMapping("/entries/{vineyardId}/type")
    public ResponseEntity<?> getDiaryEntriesByType(
            @PathVariable Long vineyardId,
            @RequestParam String type) {
        try {
            VineyardLogEntry.DiaryEntryType entryType = VineyardLogEntry.DiaryEntryType.valueOf(type);
            List<VineyardLogEntry> entries = loggingService.getDiaryEntriesByType(vineyardId, entryType);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "count", entries.size(),
                    "entries", entries.stream().map(this::diaryToMap).collect(Collectors.toList())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Invalid entry type. Valid types: " + Arrays.toString(VineyardLogEntry.DiaryEntryType.values())
            ));
        } catch (Exception e) {
            log.error("Error retrieving diary entries by type:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get diary entries containing a specific tag.
     * Query param: ?tag=budbreak
     */
    @GetMapping("/entries/{vineyardId}/tag")
    public ResponseEntity<?> getDiaryEntriesByTag(
            @PathVariable Long vineyardId,
            @RequestParam String tag) {
        try {
            List<VineyardLogEntry> entries = loggingService.getDiaryEntriesByTag(vineyardId, tag);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "count", entries.size(),
                    "entries", entries.stream().map(this::diaryToMap).collect(Collectors.toList())
            ));
        } catch (Exception e) {
            log.error("Error retrieving diary entries by tag:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get a specific diary entry by ID.
     */
    @GetMapping("/entry/{entryId}")
    public ResponseEntity<?> getEntry(@PathVariable Long entryId) {
        try {
            VineyardLogEntry entry = loggingService.getLogEntry(entryId);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "entry", diaryToMap(entry)
            ));
        } catch (Exception e) {
            log.error("Error retrieving entry:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Update a diary entry.
     * PUT /v1/vineyard-logs/entry/{entryId}
     */
    @PutMapping("/entry/{entryId}")
    public ResponseEntity<?> updateEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody VineyardDiaryEntryRequest request) {
        try {
            VineyardLogEntry.DiaryEntryType entryType = VineyardLogEntry.DiaryEntryType.valueOf(request.getEntryType());
            
            VineyardLogEntry entry = loggingService.updateLogEntry(
                    entryId,
                    request.getTitle(),
                    request.getDescription(),
                    entryType,
                    request.getGrowthStageBbch(),
                    request.getTags());

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Entry updated",
                    "entry", diaryToMap(entry)
            ));
        } catch (IllegalArgumentException e) {
            log.error("Invalid entry type provided", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Invalid entry type. Valid types: " + Arrays.toString(VineyardLogEntry.DiaryEntryType.values())
            ));
        } catch (Exception e) {
            log.error("Error updating entry:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Delete an entry.
     * DELETE /v1/vineyard-logs/entry/{entryId}
     */
    @DeleteMapping("/entry/{entryId}")
    public ResponseEntity<?> deleteEntry(@PathVariable Long entryId) {
        try {
            loggingService.deleteLogEntry(entryId);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Entry deleted"
            ));
        } catch (Exception e) {
            log.error("Error deleting entry:", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    // ============ HELPER METHODS ============

    /**
     * Convert log entry to spray-focused map (for spray operations).
     */
    private Map<String, Object> sprayToMap(VineyardLogEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.getId());
        result.put("logType", entry.getLogType());
        result.put("entryDate", entry.getEntryDate().toString());
        result.put("fungicide", entry.getFungicideProduct() != null ? entry.getFungicideProduct().getName() : null);
        result.put("disease", entry.getDisease() != null ? entry.getDisease().getCommonName() : null);
        result.put("growthStageBbch", entry.getGrowthStageBbch() != null ? entry.getGrowthStageBbch() : "N/A");
        result.put("dosageLitersPerAre", entry.getDosageLitersPerAre() != null ? String.format("%.2f", entry.getDosageLitersPerAre()) : null);
        result.put("amountAppliedLiters", entry.getAmountFungicideAppliedLiters() != null ? entry.getAmountFungicideAppliedLiters() : null);
        result.put("temperatureC", entry.getTemperatureC() != null ? entry.getTemperatureC() : "N/A");
        result.put("humidityPercent", entry.getHumidityPercent() != null ? entry.getHumidityPercent() : "N/A");
        result.put("windSpeedMsec", entry.getWindSpeedMsec() != null ? entry.getWindSpeedMsec() : "N/A");
        result.put("notes", entry.getDescription() != null ? entry.getDescription() : "");
        result.put("efficacyAssessment", entry.getEfficacyAssessment() != null ? 
                String.format("%.0f%%", entry.getEfficacyAssessment() * 100) : "Pending");
        result.put("efficacyNotes", entry.getEfficacyNotes() != null ? entry.getEfficacyNotes() : "");
        result.put("createdAt", entry.getCreatedAt());
        return result;
    }

    /**
     * Convert log entry to diary-focused map (for diary operations).
     */
    private Map<String, Object> diaryToMap(VineyardLogEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.getId());
        result.put("vineyardId", entry.getVineyard().getId());
        result.put("logType", entry.getLogType());
        result.put("entryDate", entry.getEntryDate().toString());
        result.put("title", entry.getTitle() != null ? entry.getTitle() : "");
        result.put("description", entry.getDescription() != null ? entry.getDescription() : "");
        result.put("diaryEntryType", entry.getDiaryEntryType() != null ? entry.getDiaryEntryType() : "");
        result.put("growthStageBbch", entry.getGrowthStageBbch() != null ? entry.getGrowthStageBbch() : "");
        result.put("tags", entry.getTags() != null ? entry.getTags() : "");
        result.put("createdAt", entry.getCreatedAt());
        result.put("updatedAt", entry.getUpdatedAt());
        return result;
    }
}
