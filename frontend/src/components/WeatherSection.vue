<template>
  <section id="sec-weather" class="weather-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      Current Weather
    </h2>
    <div v-show="!isCollapsed" class="weather-grid">
      <div class="weather-card">
        <span class="weather-icon">🌡️</span>
        <span class="weather-label">Temperature</span>
        <span class="weather-value">{{ currentWeather.temperatureC }}°C</span>
      </div>
      <div class="weather-card">
        <span class="weather-icon">💧</span>
        <span class="weather-label">Humidity</span>
        <span class="weather-value">{{ currentWeather.humidityPercent }}%</span>
      </div>
      <div class="weather-card">
        <span class="weather-icon">🌧️</span>
        <span class="weather-label">Precipitation</span>
        <span class="weather-value">{{ currentWeather.precipitationMm }} mm</span>
      </div>
      <div class="weather-card">
        <span class="weather-icon">💨</span>
        <span class="weather-label">Wind Speed</span>
        <span class="weather-value">{{ (currentWeather.windSpeedMsec || 0).toFixed(1) }} m/s</span>
      </div>
      <div class="weather-card" v-if="rainfallSummary">
        <span class="weather-icon">🌧️</span>
        <span class="weather-label">Rainfall (24h)</span>
        <span class="weather-value">{{ rainfallSummary.rainfall24hMm.toFixed(1) }} mm</span>
      </div>
      <div class="weather-card" v-if="rainfallSummary">
        <span class="weather-icon">🕒</span>
        <span class="weather-label">Last sig. rain (&gt;0.3 mm/h)</span>
        <span class="weather-value" v-if="rainfallSummary.hoursSinceSignificantRain > 0">{{ rainfallSummary.hoursSinceSignificantRain.toFixed(0) }}h ago</span>
        <span class="weather-value" v-else>None in 72h</span>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'WeatherSection',
  props: {
    currentWeather: { type: Object, required: true },
    rainfallSummary: { type: Object, default: null }
  },
  data() {
    return { isCollapsed: false }
  },
  methods: {
    expand() { this.isCollapsed = false }
  }
}
</script>
