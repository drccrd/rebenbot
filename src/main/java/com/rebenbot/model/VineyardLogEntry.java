package com.rebenbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * Unified vineyard log entry for both spray applications and general observations.
 * Consolidates SprayApplication and VineyardDiaryEntry into a single model.
 */
@Entity
@Table(name = "vineyard_log_entry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class VineyardLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vineyard_id", nullable = false)
    private Vineyard vineyard;

    @Column(name = "log_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private LogType logType;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    // ==== Spray-specific fields (nullable for non-spray entries) ====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fungicide_id")
    private FungicideProduct fungicideProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disease_id")
    private FungalDisease disease;

    @Column(name = "amount_fungicide_applied_liters")
    private Double amountFungicideAppliedLiters;

    // ==== General fields (for all log types) ====
    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "growth_stage_bbch")
    private String growthStageBbch;

    // ==== Weather conditions (nullable for non-spray entries) ====
    @Column(name = "temperature_c")
    private Double temperatureC;
    
    @Column(name = "humidity_percent")
    private Double humidityPercent;
    
    @Column(name = "wind_speed_msec")
    private Double windSpeedMsec;

    // ==== Diary-specific fields ====
    @Column(name = "entry_type")
    @Enumerated(EnumType.STRING)
    private DiaryEntryType diaryEntryType;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Log entry type - distinguishes between spray applications and diary entries.
     */
    public enum LogType {
        SPRAY("Spray Application"),
        OBSERVATION("Vineyard Observation"),
        WEATHER("Weather Event"),
        PEST_DISEASE("Pest or Disease Observation"),
        MAINTENANCE("Vineyard Maintenance"),
        HARVEST("Harvest Activity"),
        OTHER("Other");

        private final String displayName;

        LogType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Diary entry type (only used when logType is not SPRAY).
     */
    public enum DiaryEntryType {
        OBSERVATION("General Observation"),
        WEATHER("Weather Event"),
        PEST_DISEASE("Pest or Disease Observation"),
        MAINTENANCE("Vineyard Maintenance"),
        HARVEST("Harvest Activity"),
        OTHER("Other");

        private final String displayName;

        DiaryEntryType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
