package com.rebenbot.controller.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * Request DTO for recording spray applications.
 * Provides input validation and type safety.
 */
public class SprayApplicationRequest {

    @NotNull(message = "Vineyard ID is required")
    @Positive(message = "Vineyard ID must be positive")
    private Long vineyardId;

    @NotNull(message = "Fungicide product ID is required")
    @Positive(message = "Fungicide product ID must be positive")
    private Long fungicideId;

    @NotNull(message = "Disease ID is required")
    @Positive(message = "Disease ID must be positive")
    private Long diseaseId;

    @NotNull(message = "Application date is required")
    private LocalDateTime applicationDate;

    @NotBlank(message = "Growth stage BBCH code is required")
    @Size(max = 10, message = "BBCH code must not exceed 10 characters")
    private String growthStageBbch;

    @DecimalMin("-50")
    @DecimalMax("60")
    private Double temperatureC;

    @DecimalMin("0")
    @DecimalMax("100")
    private Double humidityPercent;

    @DecimalMin("0")
    @DecimalMax("50")
    private Double windSpeedMsec;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    // Constructors
    public SprayApplicationRequest() {
    }

    public SprayApplicationRequest(Long vineyardId, Long fungicideId, Long diseaseId,
                                   LocalDateTime applicationDate, String growthStageBbch,
                                   Double temperatureC, Double humidityPercent, Double windSpeedMsec,
                                   String notes) {
        this.vineyardId = vineyardId;
        this.fungicideId = fungicideId;
        this.diseaseId = diseaseId;
        this.applicationDate = applicationDate;
        this.growthStageBbch = growthStageBbch;
        this.temperatureC = temperatureC;
        this.humidityPercent = humidityPercent;
        this.windSpeedMsec = windSpeedMsec;
        this.notes = notes;
    }

    // Getters and Setters
    public Long getVineyardId() {
        return vineyardId;
    }

    public void setVineyardId(Long vineyardId) {
        this.vineyardId = vineyardId;
    }

    public Long getFungicideId() {
        return fungicideId;
    }

    public void setFungicideId(Long fungicideId) {
        this.fungicideId = fungicideId;
    }

    public Long getDiseaseId() {
        return diseaseId;
    }

    public void setDiseaseId(Long diseaseId) {
        this.diseaseId = diseaseId;
    }

    public LocalDateTime getApplicationDate() {
        return applicationDate;
    }

    public void setApplicationDate(LocalDateTime applicationDate) {
        this.applicationDate = applicationDate;
    }

    public String getGrowthStageBbch() {
        return growthStageBbch;
    }

    public void setGrowthStageBbch(String growthStageBbch) {
        this.growthStageBbch = growthStageBbch;
    }

    public Double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(Double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public Double getHumidityPercent() {
        return humidityPercent;
    }

    public void setHumidityPercent(Double humidityPercent) {
        this.humidityPercent = humidityPercent;
    }

    public Double getWindSpeedMsec() {
        return windSpeedMsec;
    }

    public void setWindSpeedMsec(Double windSpeedMsec) {
        this.windSpeedMsec = windSpeedMsec;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
