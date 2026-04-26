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

    private LocalDateTime lastFetchedAt = null;  // Wall clock time of last Meteoblue fetch this session

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
     * Fetch and store historical weather data from Open-Meteo free archive API.
     * Open-Meteo provides unlimited free historical weather data without API key.
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
            
            log.debug("Fetching historical weather from Open-Meteo for vineyard '{}' at ({}, {}) from {} to {}", 
                    vineyard.getName(), 
                    String.format("%.4f", lat), String.format("%.4f", lon),
                    startDate, endDate);
            
            String url = String.format(
                    "https://archive-api.open-meteo.com/v1/archive?latitude=%.2f&longitude=%.2f&start_date=%s&end_date=%s&hourly=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m&timezone=Europe/Berlin",
                    lat, lon, startDate, endDate
            );
            log.debug("Built Open-Meteo archive URL: {}", url);
            
            String response = restTemplate.getForObject(url, String.class);
            log.debug("Got response from Open-Meteo archive, response length: {}", response != null ? response.length() : "null");
            
            if (response == null) {
                log.error("RestTemplate returned null response for historical data");
                return Collections.emptyList();
            }
            
            List<WeatherData> weatherDataList = parseOpenMeteoArchiveData(response, vineyard);
            log.info("Fetched and stored {} historical weather records from Open-Meteo ({} to {})", 
                    weatherDataList.size(), startDate, endDate);
            return weatherDataList;

        } catch (Exception e) {
            log.error("Error fetching historical weather data from Open-Meteo: {}", e.getMessage(), e);
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
                LocalDateTime dateTime;
                if (timeNode.isTextual()) {
                    // Handle format: "2026-04-25 23:00" (space separator, not ISO T separator)
                    String timeStr = timeNode.asText();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    dateTime = LocalDateTime.parse(timeStr, formatter);
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

                if (!weatherDataRepository.existsByVineyardIdAndRecordedAt(vineyard.getId(), dateTime)) {
                    WeatherData saved = weatherDataRepository.save(data);
                    weatherDataList.add(saved);
                }

            } catch (Exception e) {
                log.warn("Error parsing weather record at index {}: {}", i, e.getMessage());
            }
        }

        log.info("Stored {} weather data records from Meteoblue", weatherDataList.size());
        return weatherDataList;
    }

    /**
     * Check if we last fetched recently enough and auto-fetch if needed.
     * Uses lastFetchedAt (wall clock time of last fetch) rather than recordedAt of the data,
     * because Meteoblue forecast timestamps reflect the start of each forecast hour, not when we fetched.
     * Synchronized to prevent concurrent threads from fetching duplicate data.
     */
    private synchronized void ensureFreshWeatherData() {
        try {
            LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(WEATHER_DATA_FRESHNESS_MINUTES);

            if (lastFetchedAt == null) {
                Optional<WeatherData> latestData = weatherDataRepository.findCurrentWeatherData(LocalDateTime.now());
                if (latestData.isEmpty()) {
                    log.info("No weather data found, fetching from Meteoblue...");
                } else {
                    log.info("No fetch record this session, fetching fresh data from Meteoblue...");
                }
                fetchAndStoreWeatherData(7);
                lastFetchedAt = LocalDateTime.now();
                return;
            }

            if (lastFetchedAt.isBefore(staleThreshold)) {
                long minutesOld = java.time.temporal.ChronoUnit.MINUTES.between(lastFetchedAt, LocalDateTime.now());
                log.info("Last fetch was {} minutes ago (threshold: {}), fetching fresh data from Meteoblue...",
                        minutesOld, WEATHER_DATA_FRESHNESS_MINUTES);
                fetchAndStoreWeatherData(7);
                lastFetchedAt = LocalDateTime.now();
            } else {
                long minutesOld = java.time.temporal.ChronoUnit.MINUTES.between(lastFetchedAt, LocalDateTime.now());
                log.debug("Weather data is fresh (fetched {} minutes ago, threshold: {})", minutesOld, WEATHER_DATA_FRESHNESS_MINUTES);
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

    /**
     * Parse Open-Meteo archive API response format.
     * Open-Meteo uses different JSON structure than Meteoblue.
     */
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
                // Open-Meteo format: "2026-04-01 00:00"
                LocalDateTime recordTime = LocalDateTime.parse(timeStr.replace(" ", "T"));

                double tempValue = temps.has(i) && !temps.get(i).isNull() ? temps.get(i).asDouble() : 15.0;
                double humidityValue = humidity.has(i) && !humidity.get(i).isNull() ? humidity.get(i).asDouble() : 60.0;
                double precValue = precipitation.has(i) && !precipitation.get(i).isNull() ? precipitation.get(i).asDouble() : 0.0;
                double windValueKmh = windSpeed.has(i) && !windSpeed.get(i).isNull() ? windSpeed.get(i).asDouble() : 0.0;

                WeatherData data = buildWeatherData(vineyard, recordTime, tempValue, humidityValue, precValue, windValueKmh, 0.0);
                if (!weatherDataRepository.existsByVineyardIdAndRecordedAt(vineyard.getId(), recordTime)) {
                    weatherDataList.add(data);
                    weatherDataRepository.save(data);
                }

            } catch (Exception e) {
                log.warn("Error parsing Open-Meteo weather record at index {}: {}", i, e.getMessage());
            }
        }

        return weatherDataList;
    }

}
