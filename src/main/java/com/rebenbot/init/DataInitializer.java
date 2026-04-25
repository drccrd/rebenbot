package com.rebenbot.init;

import com.rebenbot.model.FungalDisease;
import com.rebenbot.model.Vineyard;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.VineyardRepository;
import com.rebenbot.repository.WeatherDataRepository;
import com.rebenbot.service.WeatherService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

/**
 * Initialize database with demo data on application startup.
 * Initializes vineyards, diseases, and fetches current weather data via WeatherService (Meteoblue API).
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final VineyardRepository vineyardRepository;
    private final FungalDiseaseRepository diseaseRepository;
    private final WeatherService weatherService;
    private final WeatherDataRepository weatherDataRepository;

    public DataInitializer(
            VineyardRepository vineyardRepository,
            FungalDiseaseRepository diseaseRepository,
            WeatherService weatherService,
            WeatherDataRepository weatherDataRepository) {
        this.vineyardRepository = vineyardRepository;
        this.diseaseRepository = diseaseRepository;
        this.weatherService = weatherService;
        this.weatherDataRepository = weatherDataRepository;
    }

    @Override
    public void run(String... args) {
        initializeVineyards();
        initializeDiseases();
        seedHistoricalWeatherData();
        fetchInitialWeatherData();
    }

    private void initializeVineyards() {
        if (vineyardRepository.count() == 0) {
            Vineyard vineyard = Vineyard.builder()
                    .name("Schriesheim Weingut")
                    .latitude(49.27)
                    .longitude(8.40)
                    .sizeAres(10.0)
                    .region("Schriesheim, Baden-Württemberg")
                    .description("Small vineyard near Schriesheim with Spätburgunder, Syrah, and Merlot")
                    .build();

            vineyardRepository.save(vineyard);
            log.info("Initialized vineyard data");
        }
    }

    private void initializeDiseases() {
        if (diseaseRepository.count() == 0) {
            // Peronospora (Downy Mildew)
            FungalDisease peronospora = FungalDisease.builder()
                    .commonName("Peronospora")
                    .scientificName("Plasmopara viticola")
                    .germanName("Falscher Mehltau")
                    .tempMinC(10.0)
                    .tempMaxC(25.0)
                    .humidityMinPercent(85.0)
                    .description("Downy mildew - primary threat in humid conditions. Requires 10+ hours leaf wetness at 10-25°C")
                    .build();

            // Oidium (Powdery Mildew)
            FungalDisease oidium = FungalDisease.builder()
                    .commonName("Oidium")
                    .scientificName("Erysiphe necator")
                    .germanName("Echter Mehltau")
                    .tempMinC(15.0)
                    .tempMaxC(27.0)
                    .humidityMinPercent(40.0)
                    .description("Powdery mildew - thrives in warm, dry conditions. Optimal at 20-25°C, can develop at 40% humidity")
                    .build();

            diseaseRepository.save(peronospora);
            diseaseRepository.save(oidium);
            log.info("Initialized fungal disease data");
        }
    }

    private void seedHistoricalWeatherData() {
        try {
            // Skip if weather data already exists
            long weatherRecordCount = weatherDataRepository.count();
            if (weatherRecordCount > 0) {
                log.debug("Weather data already exists in database ({}), skipping historical seed", weatherRecordCount);
                return;
            }

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = LocalDate.of(endDate.getYear(), 4, 1);  // April 1 of current year
            
            log.info("Seeding historical weather data from Meteoblue API for {} to {}...", startDate, endDate);
            var historicalData = weatherService.fetchAndStoreHistoricalWeatherData(startDate, endDate);
            
            if (historicalData.isEmpty()) {
                log.warn("No historical weather data was retrieved from Meteoblue");
            } else {
                log.info("Successfully seeded {} historical weather records from Meteoblue", historicalData.size());
            }
        } catch (Exception e) {
            log.warn("Error seeding historical weather data: {}. This is non-critical, app will continue.", e.getMessage());
        }
    }

    private void fetchInitialWeatherData() {
        try {
            long weatherRecordCount = weatherDataRepository.count();
            if (weatherRecordCount > 0) {
                log.debug("Weather data already exists in database ({}), skipping initial fetch", weatherRecordCount);
                return;
            }
            
            log.info("Fetching current/forecast weather data from Meteoblue API...");
            var weatherData = weatherService.fetchAndStoreWeatherData(7);
            log.info("Successfully fetched and stored {} current/forecast weather records on startup", weatherData.size());
        } catch (Exception e) {
            log.warn("Failed to fetch weather data on startup: {}. Frontend can manually trigger fetch.", e.getMessage());
        }
    }

}