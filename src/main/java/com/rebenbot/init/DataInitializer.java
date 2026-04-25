package com.rebenbot.init;

import com.rebenbot.model.FungalDisease;
import com.rebenbot.model.Vineyard;
import com.rebenbot.model.WeatherData;
import com.rebenbot.repository.FungalDiseaseRepository;
import com.rebenbot.repository.VineyardRepository;
import com.rebenbot.repository.WeatherDataRepository;
import com.rebenbot.service.WeatherService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Initialize database with demo data on application startup.
 * Note: Fungicide data is now managed by Flyway migrations instead of runtime initialization.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final VineyardRepository vineyardRepository;
    private final FungalDiseaseRepository diseaseRepository;
    private final WeatherService weatherService;
    private final WeatherDataRepository weatherDataRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DataInitializer(
            VineyardRepository vineyardRepository,
            FungalDiseaseRepository diseaseRepository,
            WeatherService weatherService,
            WeatherDataRepository weatherDataRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.vineyardRepository = vineyardRepository;
        this.diseaseRepository = diseaseRepository;
        this.weatherService = weatherService;
        this.weatherDataRepository = weatherDataRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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

            Vineyard vineyard = vineyardRepository.findAll().stream().findFirst().orElse(null);
            if (vineyard == null) {
                log.warn("No vineyard found for weather data seeding");
                return;
            }

            // Open-Meteo API: Free historical weather archive
            // API format: https://archive-api.open-meteo.com/v1/archive?latitude=...&longitude=...&start_date=...&end_date=...
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = LocalDate.of(endDate.getYear(), 4, 1);  // April 1 of current year
            
            log.info("Fetching real historical weather data from Open-Meteo for {}/{} to {}...", startDate.getMonth(), startDate.getDayOfMonth(), endDate);
            
            String url = String.format(
                    "https://archive-api.open-meteo.com/v1/archive?latitude=%.2f&longitude=%.2f&start_date=%s&end_date=%s&hourly=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m&timezone=Europe/Berlin",
                    vineyard.getLatitude(),
                    vineyard.getLongitude(),
                    startDate,
                    endDate
            );

            log.debug("Calling Open-Meteo archive API: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isEmpty()) {
                log.warn("Empty response from Open-Meteo archive API");
                return;
            }

            List<WeatherData> historicalData = parseOpenMeteoArchiveData(response, vineyard);
            
            if (historicalData.isEmpty()) {
                log.warn("No weather data parsed from Open-Meteo response");
                return;
            }

            weatherDataRepository.saveAll(historicalData);
            log.info("Seeded {} real historical weather records (April 1-24) from Open-Meteo", historicalData.size());

            // Calculate resulting GDD by averaging hourly temps per day, then summing daily GDD
            double totalGdd = 0.0;
            LocalDate currentDay = null;
            double dayTempSum = 0.0;
            int dayCount = 0;

            for (WeatherData data : historicalData) {
                LocalDate dataDay = data.getRecordedAt().toLocalDate();
                
                if (currentDay != null && !currentDay.equals(dataDay)) {
                    // Calculate daily average and daily GDD
                    double dailyAvgTemp = dayTempSum / dayCount;
                    double dailyGdd = Math.max(0, dailyAvgTemp - 10.0);
                    totalGdd += dailyGdd;
                    
                    dayTempSum = 0.0;
                    dayCount = 0;
                }
                
                currentDay = dataDay;
                dayTempSum += data.getTemperatureC();
                dayCount++;
            }
            
            // Don't forget the last day
            if (dayCount > 0) {
                double dailyAvgTemp = dayTempSum / dayCount;
                double dailyGdd = Math.max(0, dailyAvgTemp - 10.0);
                totalGdd += dailyGdd;
            }

            log.info("Historical weather data provides approximately {}° accumulated GDD", String.format("%.1f", totalGdd));

        } catch (Exception e) {
            log.error("Error seeding historical weather data from Open-Meteo: {}", e.getMessage(), e);
        }
    }

    private List<WeatherData> parseOpenMeteoArchiveData(String jsonResponse, Vineyard vineyard) throws Exception {
        List<WeatherData> weatherDataList = new ArrayList<>();

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode hourly = root.path("hourly");

        if (hourly.isMissingNode()) {
            log.error("No hourly data in Open-Meteo response");
            return weatherDataList;
        }

        JsonNode times = hourly.path("time");
        JsonNode temps = hourly.path("temperature_2m");
        JsonNode humidity = hourly.path("relative_humidity_2m");
        JsonNode precipitation = hourly.path("precipitation");
        JsonNode windSpeed = hourly.path("wind_speed_10m");

        if (!times.isArray() || times.size() == 0) {
            log.error("Time array missing or empty in Open-Meteo response");
            return weatherDataList;
        }

        log.debug("Found {} hourly records from Open-Meteo archive", times.size());

        for (int i = 0; i < times.size(); i++) {
            try {
                String timeStr = times.get(i).asText();
                LocalDateTime recordTime = LocalDateTime.parse(timeStr.replace(" ", "T")); // "2026-04-01 00:00" -> "2026-04-01T00:00"

                double tempValue = temps.has(i) && !temps.get(i).isNull() ? temps.get(i).asDouble() : 15.0;
                double humidityValue = humidity.has(i) && !humidity.get(i).isNull() ? humidity.get(i).asDouble() : 60.0;
                double precValue = precipitation.has(i) && !precipitation.get(i).isNull() ? precipitation.get(i).asDouble() : 0.0;
                double windValue = windSpeed.has(i) && !windSpeed.get(i).isNull() ? windSpeed.get(i).asDouble() : 0.0;

                WeatherData data = WeatherData.builder()
                        .vineyard(vineyard)
                        .recordedAt(recordTime)
                        .temperatureC(tempValue)
                        .humidityPercent(humidityValue)
                        .precipitationMm(precValue)
                        .windSpeedMsec(windValue)
                        .leafWetnessIndex(0.0) // Not available from Open-Meteo archive
                        .build();

                weatherDataList.add(data);

            } catch (Exception e) {
                log.warn("Error parsing weather record at index {}: {}", i, e.getMessage());
            }
        }

        return weatherDataList;
    }

    private void fetchInitialWeatherData() {
        try {
            // Skip if weather data already exists and is recent (last hour)
            long weatherRecordCount = weatherDataRepository.count();
            if (weatherRecordCount > 0) {
                log.debug("Weather data already exists in database ({})", weatherRecordCount);
                return;
            }
            
            log.info("Fetching initial weather data from Meteoblue API...");
            var weatherData = weatherService.fetchAndStoreWeatherData(7);
            log.info("Successfully fetched and stored {} weather records on startup", weatherData.size());
        } catch (Exception e) {
            log.warn("Failed to fetch weather data on startup: {}. Frontend can manually trigger fetch.", e.getMessage());
        }
    }

}