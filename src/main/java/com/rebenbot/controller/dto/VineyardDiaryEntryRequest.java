package com.rebenbot.controller.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * Request DTO for creating vineyard diary entries.
 * Provides input validation and type safety.
 */
public class VineyardDiaryEntryRequest {

    @NotNull(message = "Vineyard ID is required")
    @Positive(message = "Vineyard ID must be positive")
    private Long vineyardId;

    @NotNull(message = "Entry date is required")
    private LocalDateTime entryDate;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    private String description;

    @NotNull(message = "Entry type is required")
    private String entryType;

    @Size(max = 10, message = "BBCH code must not exceed 10 characters")
    private String growthStageBbch;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    // Constructors
    public VineyardDiaryEntryRequest() {
    }

    public VineyardDiaryEntryRequest(Long vineyardId, LocalDateTime entryDate, String title,
                                     String description, String entryType, String growthStageBbch, String tags) {
        this.vineyardId = vineyardId;
        this.entryDate = entryDate;
        this.title = title;
        this.description = description;
        this.entryType = entryType;
        this.growthStageBbch = growthStageBbch;
        this.tags = tags;
    }

    // Getters and Setters
    public Long getVineyardId() {
        return vineyardId;
    }

    public void setVineyardId(Long vineyardId) {
        this.vineyardId = vineyardId;
    }

    public LocalDateTime getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDateTime entryDate) {
        this.entryDate = entryDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public String getGrowthStageBbch() {
        return growthStageBbch;
    }

    public void setGrowthStageBbch(String growthStageBbch) {
        this.growthStageBbch = growthStageBbch;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
