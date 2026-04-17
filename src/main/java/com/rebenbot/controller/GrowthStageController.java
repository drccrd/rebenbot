package com.rebenbot.controller;

import com.rebenbot.model.Vineyard;
import com.rebenbot.repository.VineyardRepository;
import com.rebenbot.service.GrowthStageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST API for vineyard growth stage management
 */
@RestController
@RequestMapping("/v1/growth-stage")
@CrossOrigin(origins = "*")
@Slf4j
public class GrowthStageController {

    @Autowired
    private GrowthStageService growthStageService;

    @Autowired
    private VineyardRepository vineyardRepository;

    /**
     * Get current growth stage for a vineyard (auto-calculated from GDD or manual override)
     */
    @GetMapping("/current")
    public GrowthStageService.GrowthStageInfo getCurrentGrowthStage(@RequestParam(value = "vineyardId", defaultValue = "1") Long vineyardId) {
        Vineyard vineyard = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> new RuntimeException("Vineyard not found"));
        
        return growthStageService.getCurrentGrowthStage(vineyard.getGrowthStage(), vineyard.getIsManualGrowthStage());
    }

    /**
     * Set manual growth stage override
     */
    @PostMapping("/set-manual")
    public GrowthStageService.GrowthStageInfo setManualGrowthStage(
            @RequestParam(value = "vineyardId", defaultValue = "1") Long vineyardId,
            @RequestParam String stageName) {
        
        Vineyard vineyard = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> new RuntimeException("Vineyard not found"));
        
        // Validate stage name exists
        if (!GrowthStageService.BBCH_STAGES.containsKey(stageName)) {
            throw new RuntimeException("Invalid growth stage: " + stageName);
        }
        
        vineyard.setGrowthStage(stageName);
        vineyard.setIsManualGrowthStage(true);
        vineyard.setGrowthStageLastUpdated(LocalDateTime.now());
        vineyardRepository.save(vineyard);
        
        log.info("Set manual growth stage for vineyard {} to {}", vineyardId, stageName);
        
        return growthStageService.getCurrentGrowthStage(vineyard.getGrowthStage(), vineyard.getIsManualGrowthStage());
    }

    /**
     * Switch to automatic GDD-based calculation
     */
    @PostMapping("/use-automatic")
    public GrowthStageService.GrowthStageInfo useAutomaticGrowthStage(
            @RequestParam(value = "vineyardId", defaultValue = "1") Long vineyardId) {
        
        Vineyard vineyard = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> new RuntimeException("Vineyard not found"));
        
        // Calculate GDD and update vineyard
        double gdd = growthStageService.calculateAccumulatedGdd();
        String calculatedStage = growthStageService.determineGrowthStageFromGdd(gdd);
        
        vineyard.setGrowthStage(calculatedStage);
        vineyard.setIsManualGrowthStage(false);
        vineyard.setAccumulatedGdd(gdd);
        vineyard.setGrowthStageLastUpdated(LocalDateTime.now());
        vineyardRepository.save(vineyard);
        
        log.info("Switched vineyard {} to automatic GDD calculation. Current GDD: {}, Stage: {}", 
                vineyardId, gdd, calculatedStage);
        
        return growthStageService.getCurrentGrowthStage(vineyard.getGrowthStage(), vineyard.getIsManualGrowthStage());
    }

    /**
     * Get list of available growth stages for selection
     */
    @GetMapping("/available-stages")
    public java.util.Map<String, String> getAvailableStages() {
        return GrowthStageService.BBCH_STAGES;
    }
}
