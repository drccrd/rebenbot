package com.rebenbot.controller;

import com.rebenbot.controller.dto.VineyardRequest;
import com.rebenbot.model.Vineyard;
import com.rebenbot.repository.VineyardRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/vineyards")
public class VineyardController {

    private final VineyardRepository vineyardRepository;

    public VineyardController(VineyardRepository vineyardRepository) {
        this.vineyardRepository = vineyardRepository;
    }

    @GetMapping
    public ResponseEntity<List<Vineyard>> getAllVineyards() {
        return ResponseEntity.ok(vineyardRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vineyard> getVineyardById(@PathVariable Long id) {
        return vineyardRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Vineyard> createVineyard(@Valid @RequestBody VineyardRequest request) {
        Vineyard vineyard = Vineyard.builder()
                .name(request.getName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .sizeAres(request.getSizeAres())
                .region(request.getRegion())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .build();
        
        Vineyard saved = vineyardRepository.save(vineyard);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Vineyard> updateVineyard(@PathVariable Long id, @Valid @RequestBody VineyardRequest request) {
        return vineyardRepository.findById(id)
                .map(existing -> {
                    existing.setName(request.getName());
                    existing.setLatitude(request.getLatitude());
                    existing.setLongitude(request.getLongitude());
                    existing.setSizeAres(request.getSizeAres());
                    existing.setRegion(request.getRegion());
                    existing.setDescription(request.getDescription());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return ResponseEntity.ok(vineyardRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVineyard(@PathVariable Long id) {
        if (!vineyardRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        vineyardRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
