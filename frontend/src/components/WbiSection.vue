<template>
  <section id="sec-wbi" class="wbi-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      📊 WBI Freiburg Disease Prognosis
    </h2>
    <div v-show="!isCollapsed" class="wbi-grid">

      <!-- Peronospora WBI Prognosis -->
      <div v-if="wbiPrognosis.peronospora" class="wbi-card wbi-prognosis-card">
        <div class="wbi-header">
          <h3>🍂 Peronospora (Downy Mildew)</h3>
          <span class="wbi-badge" :class="'wbi-risk-' + (wbiPrognosis.peronospora.riskLevel || '').toLowerCase()">
            {{ wbiPrognosis.peronospora.riskLevel || 'N/A' }}
          </span>
        </div>
        <div class="wbi-details">
          <p v-if="wbiPrognosis.peronospora.leafWetnessHours || wbiPrognosis.peronospora.riskScore" class="wbi-row">
            <strong>Leaf Wetness:</strong>
            {{ (wbiPrognosis.peronospora.leafWetnessHours || 0).toFixed(1) }}h /
            {{ (wbiPrognosis.peronospora.riskScore || 0).toFixed(1) }}°h
          </p>
          <p v-if="wbiPrognosis.peronospora.infectionEventCount !== null && wbiPrognosis.peronospora.infectionEventCount !== undefined" class="wbi-row">
            <strong>Infections:</strong>
            {{ wbiPrognosis.peronospora.infectionEventCount }} tracked
            ({{ wbiPrognosis.peronospora.activeIncubationEvents || 0 }} active) ·
            {{ wbiPrognosis.peronospora.soilInfectionCount || 0 }} soil ·
            {{ wbiPrognosis.peronospora.sporulationCount || 0 }} sporulation{{ wbiPrognosis.peronospora.sporulationCount !== 1 ? 's' : '' }}
          </p>
          <!-- Per-event incubation progress bars -->
          <div v-if="incubationEvents.length > 0" class="wbi-incubation-bars">
            <div v-for="evt in incubationEvents" :key="evt.id" class="incub-bar-row">
              <span class="incub-bar-label">{{ formatDateTime(evt.infectionDatetime) }}</span>
              <span v-if="isFutureEvent(evt.infectionDatetime)" class="incub-forecast-badge">forecast</span>
              <div class="incub-bar-track">
                <div class="incub-bar-fill"
                     :style="{ width: Math.min(evt.incubationPctLatest || 0, 100) + '%' }"
                     :class="{ 'incub-bar-complete': (evt.incubationPctLatest || 0) >= 100 }"></div>
              </div>
              <span class="incub-bar-pct">{{ (evt.incubationPctLatest || 0).toFixed(0) }}%</span>
            </div>
          </div>
          <p v-if="wbiPrognosis.peronospora.nextSprayDeadline" class="wbi-row wbi-spray-deadline">
            <strong>⚠ Spray by:</strong> {{ formatWbiDate(wbiPrognosis.peronospora.nextSprayDeadline) }}
          </p>
          <p v-if="wbiPrognosis.peronospora.lastSporulationDate" class="wbi-row">
            <strong>Incubation ends (est.):</strong> {{ formatWbiDate(wbiPrognosis.peronospora.lastSporulationDate) }}
          </p>
          <p class="wbi-forecast-date">
            <strong>Forecast Date:</strong> {{ formatWbiDate(wbiPrognosis.peronospora.forecastDate) }}
          </p>
        </div>
      </div>

      <!-- Oidium WBI Prognosis -->
      <div v-if="wbiPrognosis.oidium" class="wbi-card wbi-prognosis-card">
        <div class="wbi-header">
          <h3>🌬️ Oidium (Powdery Mildew)</h3>
          <span class="wbi-badge" :class="'wbi-risk-' + (wbiPrognosis.oidium.riskLevel || '').toLowerCase()">
            {{ wbiPrognosis.oidium.riskLevel || 'N/A' }}
          </span>
        </div>
        <div class="wbi-details">
          <p class="wbi-score">
            <strong>Cumulative Infection Index:</strong> {{ (wbiPrognosis.oidium.riskScore || 0).toFixed(1) }} %·h
          </p>
          <p v-if="wbiPrognosis.oidium.ontogeneticIndex !== null && wbiPrognosis.oidium.ontogeneticIndex !== undefined" class="wbi-row"
             title="Vine tissue susceptibility factor (0–1). 1.0 = fully susceptible at current growth stage.">
            <strong>Tissue Susceptibility:</strong> {{ (wbiPrognosis.oidium.ontogeneticIndex || 0).toFixed(2) }}
          </p>
          <p v-if="wbiPrognosis.oidium.oidiumDailyValue !== null && wbiPrognosis.oidium.oidiumDailyValue !== undefined" class="wbi-row"
             title="Daily infection contribution to the cumulative index (previous day, available end-of-day).">
            <strong>Yesterday's contribution:</strong> {{ (wbiPrognosis.oidium.oidiumDailyValue || 0).toFixed(3) }} %·h
          </p>
          <p class="wbi-forecast-date">
            <strong>Forecast Date:</strong> {{ formatWbiDate(wbiPrognosis.oidium.forecastDate) }}
          </p>
        </div>
      </div>

    </div>
  </section>
</template>

<script>
import { formatWbiDate, formatDateTime, isFutureEvent } from '../utils/formatters.js'

export default {
  name: 'WbiSection',
  props: {
    wbiPrognosis: { type: Object, required: true },
    incubationEvents: { type: Array, default: () => [] }
  },
  data() {
    return { isCollapsed: false }
  },
  methods: {
    formatWbiDate, formatDateTime, isFutureEvent,
    expand() { this.isCollapsed = false }
  }
}
</script>
