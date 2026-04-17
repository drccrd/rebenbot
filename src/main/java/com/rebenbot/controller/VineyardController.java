package com.rebenbot.controller;

import com.rebenbot.model.Vineyard;
import com.rebenbot.repository.VineyardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/vineyards")
@CrossOrigin(origins = "*")
public class VineyardController {

    @Autowired
    private VineyardRepository vineyardRepository;

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
    public ResponseEntity<Vineyard> createVineyard(@RequestBody Vineyard vineyard) {
        Vineyard saved = vineyardRepository.save(vineyard);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Vineyard> updateVineyard(@PathVariable Long id, @RequestBody Vineyard vineyard) {
        return vineyardRepository.findById(id)
                .map(existing -> {
                    vineyard.setId(id);
                    return ResponseEntity.ok(vineyardRepository.save(vineyard));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVineyard(@PathVariable Long id) {
        vineyardRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
