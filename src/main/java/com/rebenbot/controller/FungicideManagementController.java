package com.rebenbot.controller;

import com.rebenbot.model.*;
import com.rebenbot.repository.*;
import com.rebenbot.service.FungicideDataSyncService;
import com.rebenbot.service.FungicideService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for fungicide database management.
 * Provides access to fungicide products, FRAC codes, approvals, and rotation strategies.
 */
@RestController
@RequestMapping("/v1/fungicide-management")
@Slf4j
public class FungicideManagementController {

    private final FungicideService fungicideService;
    private final FracCodeRepository fracCodeRepository;
    private final FungicideProductRepository fungicideProductRepository;
    private final RotationStrategyRepository rotationStrategyRepository;
    private final FungalDiseaseRepository fungalDiseaseRepository;
    private final FungicideDataSyncService fungicideDataSyncService;

    public FungicideManagementController(
            FungicideService fungicideService,
            FracCodeRepository fracCodeRepository,
            FungicideProductRepository fungicideProductRepository,
            RotationStrategyRepository rotationStrategyRepository,
            FungalDiseaseRepository fungalDiseaseRepository,
            FungicideDataSyncService fungicideDataSyncService) {
        this.fungicideService = fungicideService;
        this.fracCodeRepository = fracCodeRepository;
        this.fungicideProductRepository = fungicideProductRepository;
        this.rotationStrategyRepository = rotationStrategyRepository;
        this.fungalDiseaseRepository = fungalDiseaseRepository;
        this.fungicideDataSyncService = fungicideDataSyncService;
    }

    /**
     * Get all FRAC codes (Fungicide Resistance Action Committee classifications)
     */
    @GetMapping("/frac-codes")
    public ResponseEntity<Map<String, Object>> getAllFracCodes() {
        log.debug("All FRAC codes requested");

        List<FracCode> fracCodes = fracCodeRepository.findAll();

        List<Map<String, Object>> codes = fracCodes.stream()
                .map(f -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", f.getId());
                    map.put("code", f.getCode());
                    map.put("chemicalClass", f.getChemicalClass() != null ? f.getChemicalClass() : "");
                    map.put("description", f.getDescription() != null ? f.getDescription() : "");
                    map.put("resistanceRiskLevel", f.getResistanceRiskLevel() != null ? f.getResistanceRiskLevel() : "");
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", codes.size());
        response.put("fracCodes", codes);

        return ResponseEntity.ok(response);
    }

    /**
     * Get fungicides by FRAC code (mode of action)
     */
    @GetMapping("/by-frac/{fracCode}")
    public ResponseEntity<Map<String, Object>> getFungicidesByFracCode(@PathVariable String fracCode) {
        log.debug("Fungicides requested for FRAC code: {}", fracCode);

        List<FungicideProduct> fungicides = fungicideService.getFungicidesByFracCode(fracCode);

        if (fungicides.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No fungicides found for FRAC code: " + fracCode
            ));
        }

        List<Map<String, Object>> products = fungicides.stream()
                .map(this::mapFungicideProduct)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("fracCode", fracCode);
        response.put("count", products.size());
        response.put("fungicides", products);

        return ResponseEntity.ok(response);
    }

    /**
     * Get fungicides for a specific disease
     */
    @GetMapping("/by-disease/{diseaseId}")
    public ResponseEntity<Map<String, Object>> getFungicidesForDisease(@PathVariable Long diseaseId) {
        log.debug("Fungicides requested for disease ID: {}", diseaseId);

        List<FungicideProduct> fungicides = fungicideService.getFungicidesForDisease(diseaseId);

        List<Map<String, Object>> products = fungicides.stream()
                .map(this::mapFungicideProduct)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("diseaseId", diseaseId);
        response.put("count", products.size());
        response.put("fungicides", products);

        return ResponseEntity.ok(response);
    }

    /**
     * Get rotation strategy recommendations for a disease
     */
    @GetMapping("/rotation-strategy/{diseaseId}")
    public ResponseEntity<Map<String, Object>> getRotationStrategy(@PathVariable Long diseaseId) {
        log.debug("Rotation strategy requested for disease ID: {}", diseaseId);

        Optional<RotationStrategy> strategy = rotationStrategyRepository.findByDiseaseId(diseaseId);

        if (strategy.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No rotation strategy found for disease ID: " + diseaseId
            ));
        }

        RotationStrategy rs = strategy.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("diseaseId", diseaseId);
        response.put("diseaseName", rs.getDisease() != null ? rs.getDisease().getCommonName() : "");
        response.put("recommendedFracCodes", rs.getRecommendedFracCodes() != null ? 
                rs.getRecommendedFracCodes().split(",") : new String[]{});
        response.put("minDaysBeforeRepeatingClass", rs.getMinDaysBeforeRepeatingClass() != null ? 
                rs.getMinDaysBeforeRepeatingClass() : 14);
        response.put("description", rs.getDescription() != null ? rs.getDescription() : "");

        return ResponseEntity.ok(response);
    }

    /**
     * Get recommended rotation sequence for a disease
     * Returns list of fungicides in rotation order based on FRAC codes
     */
    @GetMapping("/rotation-plan/{diseaseId}")
    public ResponseEntity<Map<String, Object>> getRotationPlan(@PathVariable Long diseaseId) {
        log.debug("Rotation plan requested for disease ID: {}", diseaseId);

        Optional<FungalDisease> disease = fungalDiseaseRepository.findById(diseaseId);
        if (disease.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "Disease not found: " + diseaseId
            ));
        }

        Optional<RotationStrategy> strategy = rotationStrategyRepository.findByDiseaseId(diseaseId);
        if (strategy.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("diseaseId", diseaseId);
            response.put("diseaseName", disease.get().getCommonName());
            response.put("minDaysBetweenRotation", 14);
            response.put("rotationSequence", List.of());
            response.put("message", "No rotation strategy configured yet. Run BVL sync and set strategy via POST /rotation-strategy.");
            return ResponseEntity.ok(response);
        }

        RotationStrategy rs = strategy.get();
        String[] fracCodes = rs.getRecommendedFracCodes() != null ? 
                rs.getRecommendedFracCodes().split(",") : new String[]{};

        List<Map<String, Object>> rotationSequence = new ArrayList<>();
        for (String fracCode : fracCodes) {
            fracCode = fracCode.trim();
            List<FungicideProduct> products = fungicideService.getFungicidesByFracCode(fracCode);
            if (!products.isEmpty()) {
                Map<String, Object> step = new HashMap<>();
                step.put("fracCode", fracCode);
                step.put("fungicides", products.stream().map(this::mapFungicideProduct).collect(Collectors.toList()));
                rotationSequence.add(step);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("diseaseId", diseaseId);
        response.put("diseaseName", disease.get().getCommonName());
        response.put("minDaysBetweenRotation", rs.getMinDaysBeforeRepeatingClass() != null ? 
                rs.getMinDaysBeforeRepeatingClass() : 14);
        response.put("rotationSequence", rotationSequence);

        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // Expiry warnings
    // -----------------------------------------------------------------------

    /**
     * Returns BVL-approved products whose authorisation expires within the next N days (default 90).
     * Use this to prompt renewal follow-up before the season starts.
     */
    @GetMapping("/approvals/expiring")
    public ResponseEntity<Map<String, Object>> getExpiringApprovals(
            @RequestParam(defaultValue = "90") int daysAhead) {
        log.debug("Expiring BVL approvals requested, days ahead: {}", daysAhead);

        List<FungicideProduct> expiring = fungicideService.getProductsWithExpiringBvlApproval(daysAhead);

        List<Map<String, Object>> items = expiring.stream()
                .map(p -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("productId", p.getId());
                    map.put("productName", p.getName());
                    map.put("activeSubstance", p.getActiveSubstance());
                    map.put("bvlApprovalExpiry", p.getBvlApprovalExpiry() != null ? p.getBvlApprovalExpiry().toString() : "");
                    map.put("phiDays", p.getPhiDays() != null ? p.getPhiDays() : 0);
                    map.put("bvlRegistrationNumber", p.getBvlRegistrationNumber() != null ? p.getBvlRegistrationNumber() : "");
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("daysAhead", daysAhead);
        response.put("count", items.size());
        response.put("message", items.isEmpty()
                ? "No BVL authorisations expiring within " + daysAhead + " days."
                : items.size() + " product(s) with BVL authorisation expiring within " + daysAhead + " days. Please verify renewal status.");
        response.put("expiringApprovals", items);

        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // CRUD — Fungicide Products
    // -----------------------------------------------------------------------

    /**
     * Create a new fungicide product.
     * Body fields: name, activeSubstance, baseDosageMlHa, phiDays, fracCode (code string, e.g. "M1")
     */
    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody Map<String, Object> body) {
        try {
            String fracCodeStr = (String) body.get("fracCode");
            FracCode fracCode = fracCodeRepository.findByCode(fracCodeStr)
                    .orElse(null);
            if (fracCode == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR",
                        "message", "Unknown FRAC code: " + fracCodeStr));
            }
            FungicideProduct product = FungicideProduct.builder()
                    .name((String) body.get("name"))
                    .activeSubstance((String) body.get("activeSubstance"))
                    .baseDosageMlHa(toDouble(body.get("baseDosageMlHa")))
                    .phiDays(toInt(body.get("phiDays")))
                    .fracCode(fracCode)
                    .build();
            FungicideProduct saved = fungicideService.saveProduct(product);
            return ResponseEntity.ok(Map.of("status", "CREATED", "id", saved.getId(), "name", saved.getName()));
        } catch (Exception e) {
            log.error("Error creating product", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", "Internal server error"));
        }
    }

    /**
     * Update an existing fungicide product (partial update — only provided fields change).
     */
    @PutMapping("/products/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Optional<FungicideProduct> updated = fungicideService.updateProduct(
                    id,
                    (String) body.get("name"),
                    (String) body.get("activeSubstance"),
                    toDouble(body.get("baseDosageMlHa")),
                    toInt(body.get("phiDays")),
                    (String) body.get("fracCode"));
            if (updated.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("status", "NOT_FOUND",
                        "message", "Product not found: " + id));
            }
            return ResponseEntity.ok(Map.of("status", "UPDATED", "id", updated.get().getId()));
        } catch (Exception e) {
            log.error("Error updating product {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", "Internal server error"));
        }
    }

    /**
     * Delete a fungicide product (cascades to approvals and target-disease records).
     */
    @DeleteMapping("/products/{id}")
    public ResponseEntity<Map<String, Object>> deleteProduct(@PathVariable Long id) {
        boolean deleted = fungicideService.deleteProduct(id);
        if (!deleted) {
            return ResponseEntity.status(404).body(Map.of("status", "NOT_FOUND",
                    "message", "Product not found: " + id));
        }
        return ResponseEntity.ok(Map.of("status", "DELETED", "id", id));
    }

    // -----------------------------------------------------------------------
    // CRUD — Fungicide Approvals
    // -----------------------------------------------------------------------

    // Approval CRUD removed — approvals are now managed via BVL PSM-API sync.
    // Use POST /api/v1/admin/sync/bvl-api to synchronise approval status.
    // See GET /approvals/expiring for upcoming expiry warnings.

    /**
     * Returns live BVL approved-use data for a product.
     * Proxies GET /awg/?kennr={kennr} and enriches with pest organism data and grape PHI.
     * Requires the product to have a BVL registration number (run sync first).
     */
    @GetMapping("/{productId}/approvals")
    public ResponseEntity<Map<String, Object>> getProductApprovals(@PathVariable Long productId) {
        Optional<FungicideProduct> productOpt = fungicideProductRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("status", "NOT_FOUND",
                    "message", "Product not found: " + productId));
        }
        FungicideProduct product = productOpt.get();
        String kennr = product.getBvlRegistrationNumber();
        if (kennr == null || kennr.isBlank()) {
            return ResponseEntity.status(404).body(Map.of("status", "NO_BVL_NUMBER",
                    "message", "Product '" + product.getName() + "' has no BVL registration number. Run BVL sync first."));
        }
        List<Map<String, Object>> approvedUses = fungicideDataSyncService.fetchApprovedUsesForKennr(kennr);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("productId", productId);
        response.put("productName", product.getName());
        response.put("bvlRegistrationNumber", kennr);
        response.put("bvlApprovalExpiry", product.getBvlApprovalExpiry() != null ? product.getBvlApprovalExpiry().toString() : null);
        response.put("source", "BVL PSM-API (live)");
        response.put("count", approvedUses.size());
        response.put("approvedUses", approvedUses);
        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // CRUD — Rotation Strategies
    // -----------------------------------------------------------------------

    /**
     * Create or update the rotation strategy for a disease.
     * Body fields: diseaseId, recommendedFracCodes (comma-separated, e.g. "M1,M4,40,U7"),
     *              minDaysBeforeRepeatingClass, description
     */
    @PostMapping("/rotation-strategy")
    public ResponseEntity<Map<String, Object>> saveRotationStrategy(@RequestBody Map<String, Object> body) {
        try {
            Long diseaseId = toLong(body.get("diseaseId"));
            String fracCodes = (String) body.get("recommendedFracCodes");
            RotationStrategy saved = fungicideService.saveRotationStrategy(
                    diseaseId, fracCodes,
                    toInt(body.get("minDaysBeforeRepeatingClass")),
                    (String) body.get("description"));
            return ResponseEntity.ok(Map.of("status", "SAVED", "id", saved.getId(),
                    "diseaseId", diseaseId, "recommendedFracCodes", fracCodes));
        } catch (Exception e) {
            log.error("Error saving rotation strategy", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", "Internal server error"));
        }
    }

    /**
     * Delete a rotation strategy by ID.
     */
    @DeleteMapping("/rotation-strategy/{id}")
    public ResponseEntity<Map<String, Object>> deleteRotationStrategy(@PathVariable Long id) {
        boolean deleted = fungicideService.deleteRotationStrategy(id);
        if (!deleted) {
            return ResponseEntity.status(404).body(Map.of("status", "NOT_FOUND",
                    "message", "Rotation strategy not found: " + id));
        }
        return ResponseEntity.ok(Map.of("status", "DELETED", "id", id));
    }

    // -----------------------------------------------------------------------
    // Rotation validation
    // -----------------------------------------------------------------------

    /**
     * Validate whether a proposed spray sequence respects resistance management rules.
     * Body fields: diseaseId, fungicideIds (list of product IDs in proposed application order)
     * Returns: valid (boolean), warnings (list of strings)
     */
    @PostMapping("/validate-rotation")
    public ResponseEntity<Map<String, Object>> validateRotation(@RequestBody Map<String, Object> body) {
        try {
            Long diseaseId = toLong(body.get("diseaseId"));
            @SuppressWarnings("unchecked")
            List<Integer> rawIds = (List<Integer>) body.get("fungicideIds");
            List<Long> fungicideIds = rawIds.stream().map(i -> (long) i).collect(Collectors.toList());

            boolean valid = fungicideService.validateRotationSequence(diseaseId, fungicideIds);

            // Build FRAC code sequence for diagnostics
            List<String> fracSequence = fungicideIds.stream()
                    .map(fid -> fungicideProductRepository.findById(fid)
                            .map(p -> p.getFracCode() != null ? p.getFracCode().getCode() : "?")
                            .orElse("unknown"))
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("diseaseId", diseaseId);
            response.put("valid", valid);
            response.put("fracSequence", fracSequence);
            response.put("message", valid
                    ? "Rotation sequence uses recommended FRAC codes — good resistance management."
                    : "Rotation sequence contains FRAC codes outside the recommended rotation. Risk of resistance development.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error validating rotation", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", "Internal server error"));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> mapFungicideProduct(FungicideProduct p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("activeSubstance", p.getActiveSubstance());
        map.put("fracCode", p.getFracCode() != null ? p.getFracCode().getCode() : "");
        map.put("fracDescription", p.getFracCode() != null ? p.getFracCode().getDescription() : "");
        map.put("resistanceRisk", p.getFracCode() != null && p.getFracCode().getResistanceRiskLevel() != null
                ? p.getFracCode().getResistanceRiskLevel().name() : "");
        map.put("baseDosageMlHa", p.getBaseDosageMlHa());
        map.put("phiDays", p.getPhiDays());
        // German product authorisation (source: BVL PSM-API)
        map.put("bvlRegistrationNumber", p.getBvlRegistrationNumber());
        map.put("bvlApprovedInGermany", p.getBvlApprovedInGermany());
        map.put("bvlApprovalExpiry", p.getBvlApprovalExpiry() != null ? p.getBvlApprovalExpiry().toString() : null);
        map.put("bvlLastVerified", p.getBvlLastVerified() != null ? p.getBvlLastVerified().toString() : null);
        return map;
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Double d) return d;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}
