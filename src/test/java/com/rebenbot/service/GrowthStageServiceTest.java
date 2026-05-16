package com.rebenbot.service;

import com.rebenbot.repository.WeatherDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GrowthStageService's pure GDD-to-stage mapping logic.
 * The WeatherDataRepository is mocked so no database is required.
 */
class GrowthStageServiceTest {

    private GrowthStageService service;

    @BeforeEach
    void setUp() {
        // Only the GDD-calculation path uses the repo; determineGrowthStageFromGdd() is pure logic.
        WeatherDataRepository mockRepo = Mockito.mock(WeatherDataRepository.class);
        service = new GrowthStageService(mockRepo);
    }

    // -----------------------------------------------------------------------
    // determineGrowthStageFromGdd — boundary cases for each threshold
    // -----------------------------------------------------------------------

    @Test
    void gdd0_returnsBudSwell() {
        assertEquals("BUD_SWELL", service.determineGrowthStageFromGdd(0.0));
    }

    @Test
    void gddJustBelowFirstLeaves_returnsBudSwell() {
        assertEquals("BUD_SWELL", service.determineGrowthStageFromGdd(4.9));
    }

    @Test
    void gddAtFirstLeaves_returnsFirstLeaves() {
        assertEquals("FIRST_LEAVES", service.determineGrowthStageFromGdd(5.0));
    }

    @Test
    void gdd20_returnsOneLeaf() {
        assertEquals("ONE_LEAF", service.determineGrowthStageFromGdd(20.0));
    }

    @Test
    void gdd128_returnsEightLeaves() {
        assertEquals("EIGHT_LEAVES", service.determineGrowthStageFromGdd(128.0));
    }

    @Test
    void gdd190_returnsInflorescenceEmergence() {
        assertEquals("INFLORESCENCE_EMERGENCE", service.determineGrowthStageFromGdd(190.0));
    }

    @Test
    void gdd290_returnsFlowering() {
        assertEquals("FLOWERING", service.determineGrowthStageFromGdd(290.0));
    }

    @Test
    void gdd400_returnsFruitSet() {
        assertEquals("FRUIT_SET", service.determineGrowthStageFromGdd(400.0));
    }

    @Test
    void gdd1000_returnsLastStage() {
        // Well above all thresholds — should return the last defined stage
        String stage = service.determineGrowthStageFromGdd(1000.0);
        assertNotNull(stage);
        // Must be a stage that is in BBCH_STAGES
        assertTrue(GrowthStageService.BBCH_STAGES.containsKey(stage),
                "Stage '" + stage + "' is not a known BBCH stage");
    }

    // -----------------------------------------------------------------------
    // BBCH code mapping
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "stage={0} → bbch={1}")
    @CsvSource({
        "BUD_SWELL,               1",
        "FIRST_LEAVES,            9",
        "ONE_LEAF,               11",
        "FLOWERING,              65",
        "FRUIT_SET,              71",
        "VERAISON,               85",
        "BERRY_RIPE,             89",
        "DORMANT,                95"
    })
    void stageToBbchMapping(String stageKey, int expectedBbch) {
        assertEquals(expectedBbch, GrowthStageService.STAGE_TO_BBCH.get(stageKey),
                "BBCH for " + stageKey);
    }

    // -----------------------------------------------------------------------
    // BBCH descriptions are present for all mapped stages
    // -----------------------------------------------------------------------

    @Test
    void allStagesHaveBbchDescriptions() {
        for (String stageKey : GrowthStageService.STAGE_TO_BBCH.keySet()) {
            assertTrue(GrowthStageService.BBCH_STAGES.containsKey(stageKey),
                    "Missing BBCH description for stage: " + stageKey);
        }
    }

    // -----------------------------------------------------------------------
    // Monotonicity: higher GDD always resolves to same-or-later stage
    // -----------------------------------------------------------------------

    @Test
    void determineGrowthStage_isMonotone() {
        double[] gddValues = {0, 5, 20, 35, 50, 65, 80, 95, 112, 128, 190, 290, 400};
        String prevStage = service.determineGrowthStageFromGdd(0);
        for (double gdd : gddValues) {
            String stage = service.determineGrowthStageFromGdd(gdd);
            int prevBbch = GrowthStageService.STAGE_TO_BBCH.getOrDefault(prevStage, 0);
            int currBbch = GrowthStageService.STAGE_TO_BBCH.getOrDefault(stage, 0);
            assertTrue(currBbch >= prevBbch,
                    "Stage regressed from BBCH " + prevBbch + " to " + currBbch + " at GDD=" + gdd);
            prevStage = stage;
        }
    }
}
