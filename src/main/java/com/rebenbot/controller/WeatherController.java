package com.rebenbot.controller;

import com.rebenbot.model.WeatherData;
import com.rebenbot.repository.WeatherDataRepository;
import com.rebenbot.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@RestController
@RequestMapping("/v1/weather")
@CrossOrigin(origins = "*")
@Slf4j
public class WeatherController {

    private final WeatherService weatherService;
    private final WeatherDataRepository weatherDataRepository;

    public WeatherController(WeatherService weatherService, WeatherDataRepository weatherDataRepository) {
        this.weatherService = weatherService;
        this.weatherDataRepository = weatherDataRepository;
    }

    /**
     * Fetch current weather data from Meteoblue and store in database.
     * Query parameter: days (default 7, max 7)
     */
    @PostMapping("/fetch")
    public ResponseEntity<?> fetchWeatherData(
            @RequestParam(value = "days", defaultValue = "7") int forecastDays) {

        log.debug("WeatherController.fetchWeatherData called with forecastDays={}", forecastDays);

        // Validate days parameter
        if (forecastDays < 1 || forecastDays > 7) {
            forecastDays = 7;
        }

        List<WeatherData> data = weatherService.fetchAndStoreWeatherData(forecastDays);

        return ResponseEntity.ok(new Object() {
            public final int recordsStored = data.size();
            public final String status = data.isEmpty() ? "ERROR" : "SUCCESS";
            public final String message = data.size() + " weather records fetched and stored";
        });
    }

    /**
     * Get all stored weather data.
     */
    @GetMapping
    public ResponseEntity<List<WeatherData>> getAllWeatherData() {
        return ResponseEntity.ok(weatherDataRepository.findAll());
    }

    /**
     * Get latest weather data record.
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestWeatherData() {
        log.debug("getLatestWeatherData called");
        return weatherService.getLatestWeatherData()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
