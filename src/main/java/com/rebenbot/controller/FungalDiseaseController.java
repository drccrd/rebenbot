package com.rebenbot.controller;

import com.rebenbot.model.FungalDisease;
import com.rebenbot.repository.FungalDiseaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/diseases")
@CrossOrigin(origins = "*")
public class FungalDiseaseController {

    @Autowired
    private FungalDiseaseRepository diseaseRepository;

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
    public ResponseEntity<FungalDisease> createDisease(@RequestBody FungalDisease disease) {
        FungalDisease saved = diseaseRepository.save(disease);
        return ResponseEntity.ok(saved);
    }

}
