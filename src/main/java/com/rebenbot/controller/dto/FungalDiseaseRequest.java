package com.rebenbot.controller.dto;

import jakarta.validation.constraints.*;

/**
 * Request DTO for fungal disease creation.
 * Provides input validation to prevent mass-assignment and invalid data.
 */
public class FungalDiseaseRequest {

    @NotBlank(message = "Disease common name is required")
    @Size(min = 1, max = 255, message = "Common name must be between 1 and 255 characters")
    private String commonName;

    @NotBlank(message = "Scientific name is required")
    @Size(min = 1, max = 255, message = "Scientific name must be between 1 and 255 characters")
    private String scientificName;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    // Constructors
    public FungalDiseaseRequest() {
    }

    public FungalDiseaseRequest(String commonName, String scientificName, String description) {
        this.commonName = commonName;
        this.scientificName = scientificName;
        this.description = description;
    }

    // Getters and Setters
    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(String scientificName) {
        this.scientificName = scientificName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
