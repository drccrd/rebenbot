package com.rebenbot.service;

import com.rebenbot.model.FracCode;
import com.rebenbot.model.FungalDisease;
import com.rebenbot.model.FungicideProduct;
import com.rebenbot.model.FungicideTargetDisease;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.FungicideProductRepository;
import com.rebenbot.repository.FungicideTargetDiseaseRepository;
import com.rebenbot.repository.InfectionRiskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FungicideRecommendationService.
 * All repositories are mocked — no database or Spring context needed.
 */
class FungicideRecommendationServiceTest {

    private FungicideProductRepository productRepository;
    private FungicideTargetDiseaseRepository targetDiseaseRepository;
    private FungalDiseaseRepository fungalDiseaseRepository;
    private InfectionRiskRepository infectionRiskRepository;

    private FungicideRecommendationService service;

    // Test fixtures
    private FungalDisease peronospora;
    private FracCode lowRiskFrac;
    private FracCode highRiskFrac;

    @BeforeEach
    void setUp() {
        productRepository = mock(FungicideProductRepository.class);
        targetDiseaseRepository = mock(FungicideTargetDiseaseRepository.class);
        fungalDiseaseRepository = mock(FungalDiseaseRepository.class);
        infectionRiskRepository = mock(InfectionRiskRepository.class);

        service = new FungicideRecommendationService(
                productRepository, targetDiseaseRepository,
                fungalDiseaseRepository, infectionRiskRepository);

        peronospora = FungalDisease.builder()
                .id(1L)
                .commonName("Peronospora")
                .scientificName("Plasmopara viticola")
                .build();

        lowRiskFrac = FracCode.builder()
                .id(1L)
                .code("M1")
                .description("Multi-site contact")
                .resistanceRiskLevel(FracCode.ResistanceRiskLevel.LOW)
                .build();

        highRiskFrac = FracCode.builder()
                .id(2L)
                .code("3")
                .description("Demethylation inhibitors")
                .resistanceRiskLevel(FracCode.ResistanceRiskLevel.HIGH)
                .build();
    }

    // -----------------------------------------------------------------------
    // Unknown disease
    // -----------------------------------------------------------------------

    @Test
    void unknownDisease_returnsEmptyList() {
        when(fungalDiseaseRepository.findByCommonName("UnknownDisease")).thenReturn(null);
        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("UnknownDisease", 0.5);
        assertTrue(result.isEmpty(), "Unknown disease should return no recommendations");
    }

    // -----------------------------------------------------------------------
    // BVL approval filtering
    // -----------------------------------------------------------------------

    @Test
    void unapprovedProduct_isExcluded() {
        FungicideProduct unapproved = FungicideProduct.builder()
                .id(10L)
                .name("Test-Fungicide")
                .activeSubstance("copper")
                .bvlApprovedInGermany(false)
                .phiDays(28)
                .fracCode(lowRiskFrac)
                .build();

        FungicideTargetDisease target = FungicideTargetDisease.builder()
                .id(1L)
                .product(unapproved)
                .disease(peronospora)
                .efficacyRating(4)
                .build();

        when(fungalDiseaseRepository.findByCommonName("Peronospora")).thenReturn(peronospora);
        when(targetDiseaseRepository.findByDiseaseId(1L)).thenReturn(List.of(target));

        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("Peronospora", 0.5);

        assertTrue(result.isEmpty(), "BVL-unapproved product must not appear in recommendations");
    }

    @Test
    void approvedProduct_isIncluded() {
        FungicideProduct approved = FungicideProduct.builder()
                .id(11L)
                .name("Approved-Fungicide")
                .activeSubstance("mancozeb")
                .bvlApprovedInGermany(true)
                .phiDays(14)
                .fracCode(lowRiskFrac)
                .build();

        FungicideTargetDisease target = FungicideTargetDisease.builder()
                .id(2L)
                .product(approved)
                .disease(peronospora)
                .efficacyRating(4)
                .build();

        when(fungalDiseaseRepository.findByCommonName("Peronospora")).thenReturn(peronospora);
        when(targetDiseaseRepository.findByDiseaseId(1L)).thenReturn(List.of(target));

        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("Peronospora", 0.5);

        assertEquals(1, result.size());
        assertEquals("Approved-Fungicide", result.get(0).fungicide.getName());
    }

    // -----------------------------------------------------------------------
    // PHI (Pre-Harvest Interval) handling
    // -----------------------------------------------------------------------

    @Test
    void phiViolation_marksAsNotApplicable() {
        FungicideProduct product = FungicideProduct.builder()
                .id(12L)
                .name("PHI-Fungicide")
                .activeSubstance("sulfur")
                .bvlApprovedInGermany(true)
                .phiDays(30)   // 30-day PHI
                .fracCode(lowRiskFrac)
                .build();

        FungicideTargetDisease target = FungicideTargetDisease.builder()
                .id(3L)
                .product(product)
                .disease(peronospora)
                .efficacyRating(3)
                .build();

        when(fungalDiseaseRepository.findByCommonName("Peronospora")).thenReturn(peronospora);
        when(targetDiseaseRepository.findByDiseaseId(1L)).thenReturn(List.of(target));

        // Only 10 days until harvest — PHI (30 days) is violated
        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("Peronospora", 0.5, 10);

        assertEquals(1, result.size());
        assertFalse(result.get(0).applicable,
                "Product with violated PHI should be marked as not applicable");
    }

    @Test
    void phiSatisfied_marksAsApplicable() {
        FungicideProduct product = FungicideProduct.builder()
                .id(13L)
                .name("PHI-Ok-Fungicide")
                .activeSubstance("sulfur")
                .bvlApprovedInGermany(true)
                .phiDays(14)
                .fracCode(lowRiskFrac)
                .build();

        FungicideTargetDisease target = FungicideTargetDisease.builder()
                .id(4L)
                .product(product)
                .disease(peronospora)
                .efficacyRating(3)
                .build();

        when(fungalDiseaseRepository.findByCommonName("Peronospora")).thenReturn(peronospora);
        when(targetDiseaseRepository.findByDiseaseId(1L)).thenReturn(List.of(target));

        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("Peronospora", 0.5, 30);

        assertEquals(1, result.size());
        assertTrue(result.get(0).applicable,
                "Product with sufficient days until harvest should be applicable");
    }

    // -----------------------------------------------------------------------
    // Sorting: higher-scored products appear first
    // -----------------------------------------------------------------------

    @Test
    void recommendations_areSortedByScoreDescending() {
        FungicideProduct lowEfficacy = FungicideProduct.builder()
                .id(20L).name("Low-Efficacy").activeSubstance("copper")
                .bvlApprovedInGermany(true).phiDays(7).fracCode(lowRiskFrac).build();
        FungicideProduct highEfficacy = FungicideProduct.builder()
                .id(21L).name("High-Efficacy").activeSubstance("fosetyl")
                .bvlApprovedInGermany(true).phiDays(7).fracCode(lowRiskFrac).build();

        FungicideTargetDisease lowTarget = FungicideTargetDisease.builder()
                .id(5L).product(lowEfficacy).disease(peronospora).efficacyRating(1).build();
        FungicideTargetDisease highTarget = FungicideTargetDisease.builder()
                .id(6L).product(highEfficacy).disease(peronospora).efficacyRating(5).build();

        when(fungalDiseaseRepository.findByCommonName("Peronospora")).thenReturn(peronospora);
        when(targetDiseaseRepository.findByDiseaseId(1L)).thenReturn(List.of(lowTarget, highTarget));

        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("Peronospora", 0.5, 60);

        assertEquals(2, result.size());
        assertTrue(result.get(0).score >= result.get(1).score,
                "Recommendations must be sorted descending by score");
    }

    // -----------------------------------------------------------------------
    // Spray timing strings
    // -----------------------------------------------------------------------

    @Test
    void highRisk_producesImmediateTiming() {
        FungicideProduct product = approvedProduct(14L, "Immediate-Fungicide", lowRiskFrac, 7);

        when(fungalDiseaseRepository.findByCommonName("Peronospora")).thenReturn(peronospora);
        when(targetDiseaseRepository.findByDiseaseId(1L)).thenReturn(
                List.of(FungicideTargetDisease.builder().id(7L).product(product)
                        .disease(peronospora).efficacyRating(4).build()));

        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("Peronospora", 0.80, 60);

        assertTrue(result.get(0).timing.startsWith("IMMEDIATE"),
                "Risk ≥ 0.75 should produce IMMEDIATE timing, got: " + result.get(0).timing);
    }

    @Test
    void lowRisk_producesPreventiveTiming() {
        FungicideProduct product = approvedProduct(15L, "Preventive-Fungicide", lowRiskFrac, 7);

        when(fungalDiseaseRepository.findByCommonName("Peronospora")).thenReturn(peronospora);
        when(targetDiseaseRepository.findByDiseaseId(1L)).thenReturn(
                List.of(FungicideTargetDisease.builder().id(8L).product(product)
                        .disease(peronospora).efficacyRating(4).build()));

        List<FungicideRecommendationService.FungicideRecommendation> result =
                service.recommendForDisease("Peronospora", 0.10, 60);

        assertTrue(result.get(0).timing.startsWith("PREVENTIVE"),
                "Risk < 0.25 should produce PREVENTIVE timing, got: " + result.get(0).timing);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private FungicideProduct approvedProduct(long id, String name, FracCode frac, int phiDays) {
        return FungicideProduct.builder()
                .id(id).name(name).activeSubstance("test-substance")
                .bvlApprovedInGermany(true).phiDays(phiDays).fracCode(frac).build();
    }
}
