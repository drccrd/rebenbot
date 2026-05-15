package com.rebenbot.controller;

import com.rebenbot.controller.dto.FungalDiseaseRequest;
import com.rebenbot.model.FungalDisease;
import com.rebenbot.repository.FungalDiseaseRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/diseases")
public class FungalDiseaseController {

    private final FungalDiseaseRepository diseaseRepository;

    public FungalDiseaseController(FungalDiseaseRepository diseaseRepository) {
        this.diseaseRepository = diseaseRepository;
    }

    @GetMapping
    public ResponseEntity<List<FungalDisease>> getAllDiseases() {
        return ResponseEntity.ok(diseaseRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FungalDisease> getDiseaseById(@PathVariable Long id) {
        return diseaseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<FungalDisease> createDisease(@Valid @RequestBody FungalDiseaseRequest request) {
        FungalDisease disease = FungalDisease.builder()
                .commonName(request.getCommonName())
                .scientificName(request.getScientificName())
                .description(request.getDescription())
                .build();
        
        FungalDisease saved = diseaseRepository.save(disease);
        return ResponseEntity.ok(saved);
    }

}
