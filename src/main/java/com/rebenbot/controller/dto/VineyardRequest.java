package com.rebenbot.controller.dto;

import jakarta.validation.constraints.*;

/**
 * Request DTO for vineyard creation and updates.
 * Provides input validation to prevent mass-assignment and invalid data.
 */
public class VineyardRequest {

    @NotBlank(message = "Vineyard name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;

    @NotNull(message = "Latitude is required")
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double longitude;

    @NotNull(message = "Size in ares is required")
    @Positive(message = "Size must be a positive number")
    private Double sizeAres;

    @Size(max = 255, message = "Region must not exceed 255 characters")
    private String region;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    // Constructors
    public VineyardRequest() {
    }

    public VineyardRequest(String name, Double latitude, Double longitude, Double sizeAres, String region, String description) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sizeAres = sizeAres;
        this.region = region;
        this.description = description;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getSizeAres() {
        return sizeAres;
    }

    public void setSizeAres(Double sizeAres) {
        this.sizeAres = sizeAres;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
