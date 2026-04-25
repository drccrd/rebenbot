package com.rebenbot.service;

import com.rebenbot.model.WeatherData;
import com.rebenbot.model.Vineyard;
import com.rebenbot.repository.WeatherDataRepository;
import com.rebenbot.repository.VineyardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Service to fetch weather data from Meteoblue API (used by vitimeteo.de).
 * Fetches hourly weather data including temperature, humidity, and leaf wetness.
 */
@Service
@Slf4j
public class WeatherService {

    private static final String METEOBLUE_API_URL = "https://my.meteoblue.com/packages/basic-1h_agro-1h";
    private static final double DEFAULT_LAT = 49.18;
    private static final double DEFAULT_LON = 8.67;
    private static final int DEFAULT_ASL = 195;
    private static final int WEATHER_DATA_FRESHNESS_MINUTES = 30;

    private final String apiKey;
    private final WeatherDataRepository weatherDataRepository;
    private final VineyardRepository vineyardRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeatherService(@Value("${meteoblue.api.key:demo}") String apiKey,
                          WeatherDataRepository weatherDataRepository,
                          VineyardRepository vineyardRepository,
                          RestTemplate restTemplate,
                          ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.weatherDataRepository = weatherDataRepository;
        this.vineyardRepository = vineyardRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<WeatherData> fetchAndStoreWeatherData(int forecastDays) {
        try {
            log.debug("fetchAndStoreWeatherData called with forecastDays={}", forecastDays);
            
            Vineyard vineyard = getOrCreateDefaultVineyard();
            if (vineyard == null) {
                log.error("No vineyard found. Cannot fetch weather data.");
                return Collections.emptyList();
            }
            
            // Use vineyard coordinates if available, otherwise use defaults
            double lat = vineyard.getLatitude() != null ? vineyard.getLatitude() : DEFAULT_LAT;
            double lon = vineyard.getLongitude() != null ? vineyard.getLongitude() : DEFAULT_LON;
            
            log.debug("Fetching weather for vineyard '{}' at ({}, {})", vineyard.getName(), 
                    String.format("%.4f", lat), String.format("%.4f", lon));
            
            String url = buildMeteoblueUrl(lat, lon, forecastDays);
            log.debug("Built Meteoblue URL: {}", url);
            
            log.debug("Calling RestTemplate.getForObject...");
            String response = restTemplate.getForObject(url, String.class);
            log.debug("Got response from Meteoblue, response length: {}", response != null ? response.length() : "null");
            
            if (response == null) {
                log.error("RestTemplate returned null response");
                return Collections.emptyList();
            }
            
            return parseAndStoreWeatherData(response, vineyard);

        } catch (Exception e) {
            log.error("Error fetching weather data from Meteoblue: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch and store historical weather data from Meteoblue archive (date_start to date_end).
     * Meteoblue's basic-1h_agro-1h package supports historical data via date parameters.
     */
    public List<WeatherData> fetchAndStoreHistoricalWeatherData(LocalDate startDate, LocalDate endDate) {
        try {
            log.info("fetchAndStoreHistoricalWeatherData called for {} to {}", startDate, endDate);
            
            Vineyard vineyard = getOrCreateDefaultVineyard();
            if (vineyard == null) {
                log.error("No vineyard found. Cannot fetch historical weather data.");
                return Collections.emptyList();
            }
            
            // Use vineyard coordinates if available, otherwise use defaults
            double lat = vineyard.getLatitude() != null ? vineyard.getLatitude() : DEFAULT_LAT;
            double lon = vineyard.getLongitude() != null ? vineyard.getLongitude() : DEFAULT_LON;
            
            log.debug("Fetching historical weather for vineyard '{}' at ({}, {}) from {} to {}", 
                    vineyard.getName(), 
                    String.format("%.4f", lat), String.format("%.4f", lon),
                    startDate, endDate);
            
            String url = buildMeteoblueArchiveUrl(lat, lon, startDate.toString(), endDate.toString());
            log.debug("Built Meteoblue archive URL: {}", url);
            
            String response = restTemplate.getForObject(url, String.class);
            log.debug("Got response from Meteoblue archive, response length: {}", response != null ? response.length() : "null");
            
            if (response == null) {
                log.error("RestTemplate returned null response for historical data");
                return Collections.emptyList();
            }
            
            List<WeatherData> weatherDataList = parseAndStoreWeatherData(response, vineyard);
            log.info("Fetched and stored {} historical weather records from Meteoblue ({} to {})", 
                    weatherDataList.size(), startDate, endDate);
            return weatherDataList;

        } catch (Exception e) {
            log.error("Error fetching historical weather data from Meteoblue: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Vineyard getOrCreateDefaultVineyard() {
        List<Vineyard> vineyards = vineyardRepository.findAll();
        if (vineyards.isEmpty()) {
            log.error("No vineyards found in database. Cannot store weather data.");
            return null;
        }
        return vineyards.get(0);
    }

    private String buildMeteoblueUrl(double lat, double lon, int forecastDays) {
        return String.format(
                "%s?lat=%.2f&lon=%.2f&asl=%d&apikey=%s&format=json&forecast_days=%d&tz=Europe/Berlin",
                METEOBLUE_API_URL,
                lat,
                lon,
                DEFAULT_ASL,
                apiKey,
                forecastDays
        );
    }

    /**
     * Build Meteoblue URL for historical archive data.
     * Meteoblue's basic-1h_agro-1h package supports both forecast_days and date_start/date_end parameters.
     */
    private String buildMeteoblueArchiveUrl(double lat, double lon, String startDate, String endDate) {
        return String.format(
                "%s?lat=%.2f&lon=%.2f&asl=%d&apikey=%s&format=json&date_start=%s&date_end=%s&tz=Europe/Berlin",
                METEOBLUE_API_URL,
                lat,
                lon,
                DEFAULT_ASL,
                apiKey,
                startDate,
                endDate
        );
    }

    private List<WeatherData> parseAndStoreWeatherData(String jsonResponse, Vineyard vineyard) throws Exception {
        List<WeatherData> weatherDataList = new ArrayList<>();

        if (vineyard == null) {
            log.error("Vineyard is null, cannot store weather data");
            return weatherDataList;
        }

        JsonNode root = objectMapper.readTree(jsonResponse);

        log.debug("Meteoblue response received, root keys: {}", root.fieldNames().hasNext() ? "has fields" : "empty");

        JsonNode data1h = root.path("data_1h");
        if (data1h.isMissingNode() || !data1h.isObject()) {
            log.error("No data_1h object in Meteoblue response. Available keys: {}", root.fieldNames());
            return weatherDataList;
        }

        String timezone = root.path("metadata").path("timezone").asText("Europe/Berlin");
        ZoneId zoneId = ZoneId.of(timezone);

        JsonNode times = data1h.path("time");
        JsonNode temps = data1h.path("temperature");
        JsonNode humidity = data1h.path("relativehumidity");
        JsonNode precipitation = data1h.path("precipitation");
        JsonNode windSpeed = data1h.path("windspeed");
        JsonNode leafWetness = data1h.path("leafwetness");

        if (!times.isArray() || times.size() == 0) {
            log.error("Time array missing or empty. data_1h keys: {}", data1h.fieldNames());
            return weatherDataList;
        }

        log.debug("Found {} time records in Meteoblue response", times.size());

        for (int i = 0; i < Math.min(times.size(), 168); i++) {
            try {
                JsonNode timeNode = times.get(i);
                log.debug("Time node {}: value={}, isNumber={}, isTextual={}", i, timeNode, timeNode.isNumber(), timeNode.isTextual());
                
                LocalDateTime dateTime;
                if (timeNode.isTextual()) {
                    // Handle format: "2026-04-25 23:00" (space separator, not ISO T separator)
                    // Meteoblue returns times in the timezone specified in metadata (usually Europe/Berlin)
                    String timeStr = timeNode.asText();
                    log.debug("Parsing timestamp: {} with timezone: {}", timeStr, timezone);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    LocalDateTime localDateTime = LocalDateTime.parse(timeStr, formatter);
                    dateTime = localDateTime.atZone(zoneId).toLocalDateTime();
                    log.debug("Converted to LocalDateTime: {} (original timezone: {})", dateTime, timezone);
                } else if (timeNode.isNumber()) {
                    // Handle Unix seconds (fallback)
                    long timeValue = timeNode.asLong();
                    log.debug("Unix timestamp (seconds): {}", timeValue);
                    long timestamp = timeValue * 1000;  // Convert seconds to milliseconds
                    dateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestamp),
                            zoneId
                    );
                } else {
                    log.warn("Unexpected time node type at index {}, skipping: {}", i, timeNode);
                    continue;
                }

                double tempValue = temps.has(i) && !temps.get(i).isNull() ? temps.get(i).asDouble() : 15.0;
                double humidityValue = humidity.has(i) && !humidity.get(i).isNull() ? humidity.get(i).asDouble() : 60.0;
                double precValue = precipitation.has(i) && !precipitation.get(i).isNull() ? precipitation.get(i).asDouble() : 0.0;
                // Meteoblue returns wind speed in km/h, convert to m/s
                double windValueKmh = windSpeed.has(i) && !windSpeed.get(i).isNull() ? windSpeed.get(i).asDouble() : 0.0;
                double windValueMsec = windValueKmh / 3.6;
                double wetness = leafWetness.has(i) && !leafWetness.get(i).isNull() ? leafWetness.get(i).asDouble() : 0.0;

                WeatherData data = WeatherData.builder()
                        .vineyard(vineyard)
                        .recordedAt(dateTime)
                        .temperatureC(tempValue)
                        .humidityPercent(humidityValue)
                        .precipitationMm(precValue)
                        .windSpeedMsec(windValueMsec)
                        .leafWetnessIndex(wetness)
                        .build();

                WeatherData saved = weatherDataRepository.save(data);
                weatherDataList.add(saved);

            } catch (Exception e) {
                log.warn("Error parsing weather record at index {}: {}", i, e.getMessage());
            }
        }

        log.info("Stored {} weather data records from Meteoblue", weatherDataList.size());
        return weatherDataList;
    }

    /**
     * Check if latest weather data is stale and auto-fetch if needed.
     */
    private void ensureFreshWeatherData() {
        try {
            Optional<WeatherData> latestData = weatherDataRepository.findCurrentWeatherData(LocalDateTime.now());
            
            if (latestData.isEmpty()) {
                log.info("No weather data found, fetching from Meteoblue...");
                fetchAndStoreWeatherData(7);
                return;
            }
            
            LocalDateTime latestRecordTime = latestData.get().getRecordedAt();
            LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(WEATHER_DATA_FRESHNESS_MINUTES);
            long minutesOld = java.time.temporal.ChronoUnit.MINUTES.between(latestRecordTime, LocalDateTime.now());
            
            if (latestRecordTime.isBefore(staleThreshold)) {
                log.info("Weather data is {} minutes old (threshold: {}), fetching fresh data from Meteoblue...",
                        minutesOld, WEATHER_DATA_FRESHNESS_MINUTES);
                fetchAndStoreWeatherData(7);
            } else {
                log.debug("Weather data is fresh ({} minutes old, threshold: {})", minutesOld, WEATHER_DATA_FRESHNESS_MINUTES);
            }
        } catch (Exception e) {
            log.warn("Error checking/refreshing weather data: {}", e.getMessage());
        }
    }

    public Optional<WeatherData> getLatestWeatherData() {
        ensureFreshWeatherData();
        return weatherDataRepository.findCurrentWeatherData(LocalDateTime.now());
    }

    /**
     * Shared utility to build WeatherData from parsed API values.
     * Handles unit conversions: wind speed km/h → m/s
     * This is the source of truth for all weather data creation.
     */
    public WeatherData buildWeatherData(Vineyard vineyard, LocalDateTime recordedAt,
                                        double temperatureC, double humidityPercent,
                                        double precipitationMm, double windSpeedKmh,
                                        double leafWetnessIndex) {
        // Convert wind speed from km/h to m/s
        double windSpeedMsec = windSpeedKmh / 3.6;
        
        return WeatherData.builder()
                .vineyard(vineyard)
                .recordedAt(recordedAt)
                .temperatureC(temperatureC)
                .humidityPercent(humidityPercent)
                .precipitationMm(precipitationMm)
                .windSpeedMsec(windSpeedMsec)
                .leafWetnessIndex(leafWetnessIndex)
                .build();
    }

}
