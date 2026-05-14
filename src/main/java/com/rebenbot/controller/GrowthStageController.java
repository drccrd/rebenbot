package com.rebenbot.controller;

import com.rebenbot.service.GrowthStageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for vineyard growth stage
 */
@RestController
@RequestMapping("/v1/growth-stage")
@CrossOrigin(origins = "*")
@Slf4j
public class GrowthStageController {

    private final GrowthStageService growthStageService;

    public GrowthStageController(GrowthStageService growthStageService) {
        this.growthStageService = growthStageService;
    }

    /**
     * Get current growth stage (GDD-calculated from weather data)
     */
    @GetMapping("/current")
    public GrowthStageService.GrowthStageInfo getCurrentGrowthStage() {
        return growthStageService.getCurrentGrowthStage();
    }
}
