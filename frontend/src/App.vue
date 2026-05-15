<template>
  <div class="app">
    <header class="header">
      <div class="header-content">
        <h1>🍇 Rebenbot</h1>
        <p>Deine Weinhelferlein</p>
      </div>
      <button @click="refreshData" :disabled="loading" class="btn-refresh">
        {{ loading ? 'Updating...' : 'Refresh' }}
      </button>
    </header>

    <main class="container">
      <!-- Status Panel -->
      <section class="status-panel" v-if="vineyard">
        <div class="status-item">
          <span class="label">Location:</span>
          <span class="value">{{ vineyard.region }}</span>
        </div>
        <div class="status-item">
          <span class="label">Size:</span>
          <span class="value">{{ vineyard.sizeAres }} ares</span>
        </div>
        <div class="status-item">
          <span class="label">Last Update:</span>
          <span class="value">{{ lastUpdate }}</span>
        </div>
      </section>

      <!-- Spray Recommendation -->
      <section class="spray-rec-section" v-if="sprayRecommendation">
        <h2 @click="toggleSection('sprayRecommendation')" class="section-header" :class="{ collapsed: collapsedSections.sprayRecommendation }">
          <span class="section-toggle">{{ collapsedSections.sprayRecommendation ? '▶' : '▼' }}</span>
          💊 Next Spray Recommendation
          <span class="rec-urgency-badge" :class="urgencyClass(sprayRecommendation.urgency)">
            {{ sprayRecommendation.urgency.replace('_', ' ') }}
          </span>
        </h2>
        <div v-show="!collapsedSections.sprayRecommendation" class="spray-rec-card" :class="urgencyClass(sprayRecommendation.urgency)">
          <p class="rec-explanation">{{ sprayRecommendation.explanation }}</p>

          <div class="rec-date-row">
            <div class="rec-date-block">
              <span class="rec-date-label">Target date</span>
              <span class="rec-date-value">{{ formatDate(sprayRecommendation.targetDate) }}</span>
            </div>
            <div class="rec-date-block">
              <span class="rec-date-label">Allowable window</span>
              <span class="rec-date-value">{{ formatDate(sprayRecommendation.windowStart) }} – {{ formatDate(sprayRecommendation.windowEnd) }}</span>
            </div>
            <div class="rec-date-block">
              <span class="rec-date-label">Days until target</span>
              <span class="rec-date-value">{{ sprayRecommendation.daysUntilTarget }}</span>
            </div>
            <div class="rec-date-block" v-if="sprayRecommendation.daysSinceLastSpray !== null">
              <span class="rec-date-label">Days since last spray</span>
              <span class="rec-date-value">{{ sprayRecommendation.daysSinceLastSpray }}</span>
            </div>
            <div class="rec-date-block">
              <span class="rec-date-label">Recommended interval</span>
              <span class="rec-date-value">{{ sprayRecommendation.recommendedIntervalDays }} days</span>
            </div>
          </div>

          <div class="rec-wbi-row" v-if="sprayRecommendation.wbiPeronospora || sprayRecommendation.wbiOidium">
            <div class="rec-wbi-chip" v-if="sprayRecommendation.wbiPeronospora"
                 :class="sprayRecommendation.wbiPeronospora.riskLevel === 'INFECTION_RISK' ? 'wbi-risk-infection_risk' : 'wbi-risk-no_infection'">
              <strong>Peronospora</strong>
              {{ sprayRecommendation.wbiPeronospora.riskLevel }} — {{ (sprayRecommendation.wbiPeronospora.riskScore || 0).toFixed(1) }} dh
              <span v-if="sprayRecommendation.wbiPeronospora.nextSprayDeadline">
                · spray by {{ formatDate(sprayRecommendation.wbiPeronospora.nextSprayDeadline) }}
              </span>
              <div class="wbi-forecast-date">WBI forecast: {{ formatDate(sprayRecommendation.wbiPeronospora.forecastDate) }}</div>
            </div>
            <div class="rec-wbi-chip" v-if="sprayRecommendation.wbiOidium"
                 :class="sprayRecommendation.wbiOidium.riskLevel === 'INFECTION_RISK' ? 'wbi-risk-infection_risk' : 'wbi-risk-no_infection'">
              <strong>Oidium</strong>
              {{ sprayRecommendation.wbiOidium.riskLevel }} — {{ (sprayRecommendation.wbiOidium.riskScore || 0).toFixed(1) }} %·h
              <div class="wbi-forecast-date">WBI forecast: {{ formatDate(sprayRecommendation.wbiOidium.forecastDate) }}</div>
            </div>
          </div>

          <details class="rec-factors-details">
            <summary>Driving factors</summary>
            <dl class="rec-factors-list">
              <template v-for="(value, key) in sprayRecommendation.drivingFactors" :key="key">
                <dt>{{ key.replace(/_/g, ' ') }}</dt>
                <dd>{{ value }}</dd>
              </template>
            </dl>
          </details>
        </div>
      </section>

      <!-- Current Weather -->
      <section class="weather-section" v-if="currentWeather">        <h2 @click="toggleSection('weather')" class="section-header" :class="{ collapsed: collapsedSections.weather }">
          <span class="section-toggle">{{ collapsedSections.weather ? '▶' : '▼' }}</span>
          Current Weather
        </h2>
        <div v-show="!collapsedSections.weather" class="weather-grid">
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

      <!-- Growth Stage -->
      <section class="growth-stage-section" v-if="growthStage">
        <h2 @click="toggleSection('growthStage')" class="section-header" :class="{ collapsed: collapsedSections.growthStage }">
          <span class="section-toggle">{{ collapsedSections.growthStage ? '▶' : '▼' }}</span>
          Vine Growth Stage
        </h2>
        <div v-show="!collapsedSections.growthStage" class="growth-stage-card">

          <!-- Primary: VitiMeteo observed today -->
          <div v-if="latestPheno" class="gs-vitimeteo-block">
            <div class="gs-source-line">
              <span class="gs-source-badge">VitiMeteo Freiburg</span>
              <span class="gs-source-date">as of {{ formatWbiDate(latestPheno.phenoDate) }}</span>
            </div>
            <!-- Current shoot stage (BBCH 11-19 = leaf count) -->
            <div class="gs-primary-stage">
              <span class="gs-bbch-badge">BBCH {{ latestPheno.bbchCode }}</span>
              <span class="gs-stage-label">{{ bbchLabel(latestPheno.bbchCode) }}</span>
            </div>
            <!-- Leaf metrics -->
            <div class="gs-metrics-row">
              <span v-if="latestPheno.leafCount !== null" class="gs-metric">
                🍃 {{ latestPheno.leafCount.toFixed(0) }} leaves
              </span>
              <span v-if="latestPheno.leafAreaCm2 !== null" class="gs-metric"
                    title="Total leaf area per shoot including growing and mature leaves">
                📐 {{ latestPheno.leafAreaCm2.toFixed(0) }} cm²
              </span>
              <span v-if="latestPheno.huglinIndex !== null" class="gs-metric"
                    title="Huglin heliothermique index — cumulative vine heat from April 1">
                🌡 Huglin {{ latestPheno.huglinIndex.toFixed(0) }}°
              </span>
            </div>
          </div>

          <!-- Inflorescence / susceptibility status -->
          <div class="gs-infl-block" :class="effectiveBbch >= 53 ? 'gs-infl-active' : 'gs-infl-inactive'">
            <template v-if="effectiveBbch >= 53">
              <span class="gs-infl-icon">🍇</span>
              <div>
                <div class="gs-infl-title">Inflorescence reached — peak BBCH {{ effectiveBbch }}</div>
                <div v-if="effectiveBbch <= 79" class="gs-oidium-warning">
                  ⚠️ Oidium susceptibility window active (BBCH 53–79)
                </div>
                <div v-else class="gs-oidium-clear">
                  ✓ Outside oidium susceptibility window
                </div>
              </div>
            </template>
            <template v-else>
              <span class="gs-infl-icon">🌿</span>
              <div class="gs-infl-title muted">Inflorescence not yet observed (below BBCH 53)</div>
            </template>
          </div>

          <!-- GDD reference -->
          <div class="gs-gdd-row" :title="'Growing Degree Days accumulated from April 1 (base 10°C). Used as a local cross-check alongside VitiMeteo.'">
            📊 Local GDD estimate: {{ growthStage.currentGdd.toFixed(0) }}° → {{ growthStage.shootStageName.replace(/^BBCH \d+ — /, '') }}
          </div>

        </div>
      </section>

      <!-- WBI Freiburg Disease Prognosis -->
      <section class="wbi-section" v-if="wbiPrognosis.peronospora || wbiPrognosis.oidium">
        <h2 @click="toggleSection('wbiPrognosis')" class="section-header" :class="{ collapsed: collapsedSections.wbiPrognosis }">
          <span class="section-toggle">{{ collapsedSections.wbiPrognosis ? '▶' : '▼' }}</span>
          📊 WBI Freiburg Disease Prognosis
        </h2>
        <div v-show="!collapsedSections.wbiPrognosis" class="wbi-grid">
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

      <!-- Disease Risk Assessment -->
      <section class="risk-section" v-if="risks.length > 0">
        <h2 @click="toggleSection('riskAssessment')" class="section-header" :class="{ collapsed: collapsedSections.riskAssessment }">
          <span class="section-toggle">{{ collapsedSections.riskAssessment ? '▶' : '▼' }}</span>
          Infection Risk Assessment
        </h2>
        <div v-show="!collapsedSections.riskAssessment" class="risk-grid">
          <div 
            v-for="risk in risks" 
            :key="risk.id"
            class="risk-card"
            :class="'risk-' + risk.riskLevel.toLowerCase()"
          >
            <div class="risk-header">
              <h3>{{ risk.disease.commonName }}</h3>
              <span class="risk-badge" :title="risk.calculationBreakdown || 'Risk assessment based on current weather conditions'">{{ risk.riskLevel }}</span>
            </div>
            <div class="risk-score-bar">
              <div class="risk-bar" :style="{ width: (risk.riskScore * 100) + '%' }"></div>
            </div>
            <div class="risk-details">
              <p class="risk-score" :title="risk.calculationBreakdown || 'Percentage likelihood of disease development'">Score: {{ (risk.riskScore * 100).toFixed(0) }}%</p>
              <p class="risk-optimal" :title="risk.calculationBreakdown || 'Calculation factors and weather conditions'">Optimal conditions: {{ formatOptimalConditions(risk.disease) }}</p>
              <p class="risk-recommendation">{{ risk.recommendation }}</p>
            </div>
          </div>
        </div>
      </section>


      <!-- Spray Diary -->
      <section class="spray-diary-section">
        <h2 @click="toggleSection('sprayLog')" class="section-header" :class="{ collapsed: collapsedSections.sprayLog }">
          <span class="section-toggle">{{ collapsedSections.sprayLog ? '▶' : '▼' }}</span>
          Spray Diary
        </h2>
        <div v-show="!collapsedSections.sprayLog" class="diary-container">
          <!-- Log New Entry Form -->
          <div class="log-spray-card">
            <div class="form-mode-toggle">
              <button 
                @click="entryMode = 'spray'"
                :class="['mode-btn', { active: entryMode === 'spray' }]"
              >
                💊 Record Spray
              </button>
              <button 
                @click="entryMode = 'note'"
                :class="['mode-btn', { active: entryMode === 'note' }]"
              >
                📝 Add Diary Note
              </button>
            </div>

            <h3 v-if="entryMode === 'spray'">💊 Log Spray Application</h3>
            <h3 v-else>📝 Add Diary Entry</h3>

            <form @submit.prevent="recordEntry" class="spray-form">
              <!-- SPRAY MODE FIELDS -->
              <template v-if="entryMode === 'spray'">
                <div class="form-row">
                  <div class="form-group">
                    <label for="fungicide-select">Fungicide <span class="required">*</span></label>
                    <select id="fungicide-select" v-model="newSpray.fungicideId" required>
                      <option value="">Select a fungicide...</option>
                      <option v-for="fung in fungicides" :key="fung.id" :value="fung.id">
                        {{ fung.name }}
                      </option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label for="disease-select">Disease Target <span class="required">*</span></label>
                    <select id="disease-select" v-model="newSpray.diseaseId" required>
                      <option value="">Select disease...</option>
                      <option v-for="disease in diseases" :key="disease.id" :value="disease.id">
                        {{ disease.commonName }}
                      </option>
                    </select>
                  </div>
                </div>

                <div class="form-row">
                  <div class="form-group">
                    <label for="spray-date">Application Date & Time <span class="required">*</span></label>
                    <input id="spray-date" type="datetime-local" v-model="newSpray.applicationDate" required />
                  </div>
                  <div class="form-group">
                    <label for="growth-stage">Growth Stage (BBCH)</label>
                    <input id="growth-stage" type="text" v-model="newSpray.growthStageBbch" placeholder="e.g., 75" />
                  </div>
                </div>

                <div class="form-row">
                  <div class="form-group">
                    <label for="temp">Temperature (°C)</label>
                    <input id="temp" type="number" v-model.number="newSpray.temperatureC" step="0.1" />
                  </div>
                  <div class="form-group">
                    <label for="humidity">Humidity (%)</label>
                    <input id="humidity" type="number" v-model.number="newSpray.humidityPercent" step="0.1" min="0" max="100" />
                  </div>
                  <div class="form-group">
                    <label for="wind">Wind Speed (m/s)</label>
                    <input id="wind" type="number" v-model.number="newSpray.windSpeedMsec" step="0.1" min="0" />
                  </div>
                </div>

                <div class="form-row">
                  <div class="form-group">
                    <label for="amount">Fungicide Amount Applied (liters)</label>
                    <input id="amount" type="number" v-model.number="newSpray.amountFungicideAppliedLiters" step="0.01" min="0.01" placeholder="Min 0.01 L (10ml)" />
                  </div>
                </div>
              </template>

              <!-- DIARY NOTE MODE FIELDS -->
              <template v-else>
                <div class="form-row">
                  <div class="form-group">
                    <label for="note-date">Date & Time <span class="required">*</span></label>
                    <input id="note-date" type="datetime-local" v-model="newSpray.applicationDate" required />
                  </div>
                  <div class="form-group">
                    <label for="note-type">Entry Type <span class="required">*</span></label>
                    <select id="note-type" v-model="newSpray.entryType" required>
                      <option value="">Select type...</option>
                      <option value="OBSERVATION">Observation</option>
                      <option value="WEATHER">Weather</option>
                      <option value="PEST_DISEASE">Pest/Disease</option>
                      <option value="MAINTENANCE">Maintenance</option>
                      <option value="HARVEST">Harvest</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>
                </div>

                <div class="form-row">
                  <div class="form-group">
                    <label for="note-title">Title <span class="required">*</span></label>
                    <input id="note-title" type="text" v-model="newSpray.title" placeholder="e.g., Early budbreak observed" required />
                  </div>
                </div>

                <div class="form-row">
                  <div class="form-group">
                    <label for="note-growth">Growth Stage (BBCH)</label>
                    <input id="note-growth" type="text" v-model="newSpray.growthStageBbch" placeholder="e.g., 09" />
                  </div>
                </div>
              </template>

              <!-- COMMON FIELDS -->
              <div class="form-row">
                <div class="form-group full-width">
                  <label for="notes">Notes / Description</label>
                  <textarea id="notes" v-model="newSpray.notes" :placeholder="entryMode === 'spray' ? 'Conditions, application notes, etc.' : 'Detailed observation or notes'"></textarea>
                </div>
              </div>

              <div v-if="entryMode === 'note'" class="form-row">
                <div class="form-group full-width">
                  <label for="tags">Tags (comma-separated)</label>
                  <input id="tags" type="text" v-model="newSpray.tags" placeholder="e.g., spring, budbreak, phenology" />
                </div>
              </div>

              <button type="submit" :disabled="recordingSpray" class="btn-submit">
                {{ recordingSpray ? 'Saving...' : (entryMode === 'spray' ? 'Record Spray' : 'Add Note') }}
              </button>
            </form>
          </div>

          <!-- Recent Sprays -->
          <div class="recent-sprays-card" v-if="recentSprays.length > 0">
            <h3>📋 Recent Applications (Last 7 Days)</h3>
            <div class="sprays-list">
              <div v-for="spray in recentSprays" :key="spray.id" class="spray-item">
                <div class="spray-header">
                  <span class="spray-fungicide">{{ spray.fungicide }}</span>
                  <span class="spray-date">{{ spray.applicationDate }}</span>
                </div>
                <div class="spray-info">
                  <span class="info-label">Disease:</span>
                  <span class="info-value">{{ spray.disease }}</span>
                </div>
                <div class="spray-info" v-if="spray.dosageLitersPerAre !== 'N/A'">
                  <span class="info-label">Dosage:</span>
                  <span class="info-value">{{ spray.dosageLitersPerAre }}L/are</span>
                </div>
                <div class="spray-info" v-if="spray.efficacyAssessment !== 'Pending'">
                  <span class="info-label">Effectiveness:</span>
                  <span class="info-value">{{ spray.efficacyAssessment }}</span>
                </div>
                <div class="spray-info" v-if="spray.notes">
                  <span class="info-label">Notes:</span>
                  <span class="info-value">{{ spray.notes }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="no-sprays">
            <p>📭 No spray applications recorded yet</p>
          </div>
        </div>
      </section>

      <!-- Season Buying Decision (Phase 1) -->
      <section class="season-planner-section">
        <h2 @click="toggleSection('seasonPlanner')" class="section-header" :class="{ collapsed: collapsedSections.seasonPlanner }">
          <span class="section-toggle">{{ collapsedSections.seasonPlanner ? '▶' : '▼' }}</span>
          🛒 Season Buying Decision
        </h2>
        <div v-show="!collapsedSections.seasonPlanner">
          <div v-if="Object.keys(fungicidesByDisease).length === 0" class="no-data-message">
            Run <strong>BVL Sync</strong> in the Data Sync section to load approved products first.
          </div>
          <template v-else>
            <div class="planner-config-strip">
              <div class="config-item">
                <label>Protect against:</label>
                <div class="disease-pill-group">
                  <button :class="['disease-pill', { active: planTargets.peronospora }]" @click="planTargets.peronospora = !planTargets.peronospora">🍂 Peronospora</button>
                  <button :class="['disease-pill', { active: planTargets.oidium }]" @click="planTargets.oidium = !planTargets.oidium">🌬 Oidium</button>
                </div>
              </div>
              <div class="config-item">
                <label>Sprays per disease this season: <strong>{{ planSprayCount }}</strong></label>
                <input type="range" v-model.number="planSprayCount" min="4" max="12" step="1" class="spray-count-slider" />
                <div class="slider-hint">4 = minimal &nbsp;·&nbsp; 7 = standard (BW recommendation) &nbsp;·&nbsp; 12 = intensive</div>
              </div>
            </div>
            <div v-if="expiringApprovals.length > 0" class="expiry-warning-banner">
              <div class="expiry-banner-header" @click="collapsedSections.expiryBanner = !collapsedSections.expiryBanner">
                <strong>⚠ BVL Approval Expiry Alerts — {{ expiringApprovals.length }} product{{ expiringApprovals.length !== 1 ? 's' : '' }} affected (next 120 days)</strong>
                <span class="expiry-chevron">{{ collapsedSections.expiryBanner ? '▶' : '▼' }}</span>
              </div>
              <ul v-show="!collapsedSections.expiryBanner" class="expiry-list">
                <li v-for="a in expiringApprovals" :key="a.productId">
                  <strong>{{ a.productName }}</strong> — BVL authorisation expires <strong>{{ a.bvlApprovalExpiry }}</strong>
                </li>
              </ul>
            </div>
            <!-- Peronospora slots -->
            <div v-if="planTargets.peronospora" class="disease-slot-block">
              <h3 class="disease-slot-title">🍂 Peronospora — Rotation Slots</h3>
              <p class="frac-source">Source: FRAC CAA Working Group &amp; Phenylamide Expert Forum recommendations for <em>Plasmopara viticola</em></p>
              <div v-for="slot in activePeronSlots" :key="slot.id" class="frac-slot-card">
                <div class="slot-meta">
                  <div class="slot-badges">
                    <span class="slot-pos-badge">{{ slot.label }}</span>
                    <span v-for="fc in slot.fracCodes" :key="fc" class="frac-code-badge">FRAC {{ fc }}</span>
                    <span v-if="slot.optional" class="optional-tag">optional</span>
                  </div>
                  <div class="slot-title-row">
                    <strong>{{ slot.name }}</strong>
                    <span class="slot-uses">Used {{ peronSequence.filter(s => s === slot.id).length }}× this season</span>
                  </div>
                  <div class="slot-rule-text">{{ slot.rule }}</div>
                  <div v-if="slot.warning" class="slot-warning-text">⚠ {{ slot.warning }}</div>
                </div>
                <p class="multi-select-hint">Click to select/deselect. Multiple products rotate through this slot.</p>
                <div class="slot-product-list">
                  <div v-if="peronProductsForSlot(slot).length === 0" class="no-slot-products">
                    No products found for FRAC {{ slot.fracCodes.join(' / ') }} linked to Peronospora — run BVL Sync to populate.
                  </div>
                  <div
                    v-for="product in peronProductsForSlot(slot)"
                    :key="product.id"
                    class="product-radio-card"
                    :class="{
                      selected: selectedSlots.peronospora[slot.id].includes(product.id),
                      'frac-unverified': product.fracUnknown
                    }"
                    @click="toggleSlotProduct('peronospora', slot.id, product.id)"
                  >
                    <div class="prc-header">
                      <span class="prc-name">{{ product.name }}</span>
                      <span v-if="product.fracUnknown" class="frac-unknown-badge" title="FRAC code not yet resolved from BVL — may fit this slot">FRAC?</span>
                      <span v-else class="frac-verified-badge">FRAC {{ product.fracCode }}</span>
                    </div>
                    <div class="prc-sub">{{ product.activeSubstance }}</div>
                    <div class="prc-info-grid">
                      <span v-if="product.baseDosageMlHa && vineyard" class="prc-info-item">
                        <span class="prc-info-label">Per app:</span>
                        {{ Math.round(product.baseDosageMlHa * vineyard.sizeAres / 10000) }} mL
                        <span class="prc-info-sub">({{ product.baseDosageMlHa.toFixed(0) }} mL/ha)</span>
                      </span>
                      <span v-if="product.phiDays" class="prc-info-item">
                        <span class="prc-info-label">PHI:</span> {{ product.phiDays }} days
                      </span>
                      <span v-if="product.manufacturerName" class="prc-info-item prc-info-mfg">
                        {{ product.manufacturerName }}
                      </span>
                      <span v-if="product.bvlRegistrationNumber" class="prc-info-item prc-info-reg">
                        Kennz. {{ product.bvlRegistrationNumber }}
                      </span>
                      <span v-if="product.concentration" class="prc-info-item">
                        <span class="prc-info-label">Conc.:</span> {{ product.concentration }}%
                      </span>
                    </div>
                    <div v-if="expiringApprovals.find(a => a.productId === product.id)" class="prc-expiry">
                      ⚠ BVL expires {{ expiringApprovals.find(a => a.productId === product.id).bvlApprovalExpiry }}
                    </div>
                    <div class="prc-selected-check" v-if="selectedSlots.peronospora[slot.id].includes(product.id)">✓ selected</div>
                  </div>
                </div>
                <div v-if="selectedSlots.peronospora[slot.id].length > 0" class="slot-qty-row">
                  <div v-for="(productId, pi) in selectedSlots.peronospora[slot.id]" :key="productId">
                    ✓ <strong>{{ getProductName(productId) }}</strong>:
                    buy {{ calcBuyQtyForProductInSlot(productId, pi, selectedSlots.peronospora[slot.id].length, peronSequence.filter(s => s === slot.id).length) }}
                    ({{ productUsesInSlot(peronSequence.filter(s => s === slot.id).length, selectedSlots.peronospora[slot.id].length, pi) }} application{{ productUsesInSlot(peronSequence.filter(s => s === slot.id).length, selectedSlots.peronospora[slot.id].length, pi) !== 1 ? 's' : '' }})
                  </div>
                </div>
              </div>
            </div>
            <!-- Oidium slots -->
            <div v-if="planTargets.oidium" class="disease-slot-block">
              <h3 class="disease-slot-title">🌬 Oidium — Rotation Slots</h3>
              <p class="frac-source">Source: FRAC SBI Working Group recommendations for <em>Erysiphe necator</em></p>
              <div v-for="slot in activeOidiumSlots" :key="slot.id" class="frac-slot-card">
                <div class="slot-meta">
                  <div class="slot-badges">
                    <span class="slot-pos-badge">{{ slot.label }}</span>
                    <span v-for="fc in slot.fracCodes" :key="fc" class="frac-code-badge">FRAC {{ fc }}</span>
                    <span v-if="slot.optional" class="optional-tag">optional</span>
                  </div>
                  <div class="slot-title-row">
                    <strong>{{ slot.name }}</strong>
                    <span class="slot-uses">Used {{ oidiumSequence.filter(s => s === slot.id).length }}× this season</span>
                  </div>
                  <div class="slot-rule-text">{{ slot.rule }}</div>
                  <div v-if="slot.warning" class="slot-warning-text">⚠ {{ slot.warning }}</div>
                </div>
                <p class="multi-select-hint">Click to select/deselect. Multiple products rotate through this slot.</p>
                <div class="slot-product-list">
                  <div v-if="oidiumProductsForSlot(slot).length === 0" class="no-slot-products">
                    No products found for FRAC {{ slot.fracCodes.join(' / ') }} linked to Oidium — run BVL Sync to populate.
                  </div>
                  <div
                    v-for="product in oidiumProductsForSlot(slot)"
                    :key="product.id"
                    class="product-radio-card"
                    :class="{
                      selected: selectedSlots.oidium[slot.id].includes(product.id),
                      'frac-unverified': product.fracUnknown
                    }"
                    @click="toggleSlotProduct('oidium', slot.id, product.id)"
                  >
                    <div class="prc-header">
                      <span class="prc-name">{{ product.name }}</span>
                      <span v-if="product.fracUnknown" class="frac-unknown-badge" title="FRAC code not yet resolved from BVL — may fit this slot">FRAC?</span>
                      <span v-else class="frac-verified-badge">FRAC {{ product.fracCode }}</span>
                    </div>
                    <div class="prc-sub">{{ product.activeSubstance }}</div>
                    <div class="prc-info-grid">
                      <span v-if="product.baseDosageMlHa && vineyard" class="prc-info-item">
                        <span class="prc-info-label">Per app:</span>
                        {{ Math.round(product.baseDosageMlHa * vineyard.sizeAres / 10000) }} mL
                        <span class="prc-info-sub">({{ product.baseDosageMlHa.toFixed(0) }} mL/ha)</span>
                      </span>
                      <span v-if="product.phiDays" class="prc-info-item">
                        <span class="prc-info-label">PHI:</span> {{ product.phiDays }} days
                      </span>
                      <span v-if="product.manufacturerName" class="prc-info-item prc-info-mfg">
                        {{ product.manufacturerName }}
                      </span>
                      <span v-if="product.bvlRegistrationNumber" class="prc-info-item prc-info-reg">
                        Kennz. {{ product.bvlRegistrationNumber }}
                      </span>
                      <span v-if="product.concentration" class="prc-info-item">
                        <span class="prc-info-label">Conc.:</span> {{ product.concentration }}%
                      </span>
                    </div>
                    <div v-if="expiringApprovals.find(a => a.productId === product.id)" class="prc-expiry">
                      ⚠ BVL expires {{ expiringApprovals.find(a => a.productId === product.id).bvlApprovalExpiry }}
                    </div>
                    <div class="prc-selected-check" v-if="selectedSlots.oidium[slot.id].includes(product.id)">✓ selected</div>
                  </div>
                </div>
                <div v-if="selectedSlots.oidium[slot.id].length > 0" class="slot-qty-row">
                  <div v-for="(productId, pi) in selectedSlots.oidium[slot.id]" :key="productId">
                    ✓ <strong>{{ getProductName(productId) }}</strong>:
                    buy {{ calcBuyQtyForProductInSlot(productId, pi, selectedSlots.oidium[slot.id].length, oidiumSequence.filter(s => s === slot.id).length) }}
                    ({{ productUsesInSlot(oidiumSequence.filter(s => s === slot.id).length, selectedSlots.oidium[slot.id].length, pi) }} application{{ productUsesInSlot(oidiumSequence.filter(s => s === slot.id).length, selectedSlots.oidium[slot.id].length, pi) !== 1 ? 's' : '' }})
                  </div>
                </div>
              </div>
            </div>
            <!-- Shopping summary + confirm -->
            <div class="planner-summary-block">
              <h3>🛒 Your Shopping List</h3>
              <div v-if="shoppingList.length === 0" class="no-data-message">
                Select at least one product per active slot above to generate your shopping list.
              </div>
              <div v-else>
                <table class="shopping-table">
                  <thead>
                    <tr>
                      <th>Product</th><th>Active Substance</th><th>FRAC</th><th>Applications</th><th>Buy (total)</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in shoppingList" :key="item.productId">
                      <td>
                        {{ item.productName }}
                        <div v-if="item.expiringApproval" class="expiry-inline">⚠ BVL expires {{ item.expiringApproval }}</div>
                      </td>
                      <td>{{ item.activeSubstance }}</td>
                      <td><span class="frac-code-badge">{{ item.fracCode }}</span></td>
                      <td>{{ item.applications }}×</td>
                      <td>
                        <strong v-if="item.totalMl">{{ item.totalMl >= 1000 ? (item.totalMl / 1000).toFixed(2) + ' L' : item.totalMl + ' mL' }}</strong>
                        <span v-else class="qty-unknown">—</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <div class="confirm-block">
                  <p v-if="purchasesConfirmed" class="confirmed-msg">✅ Purchases confirmed — spray plan generated below.</p>
                  <button class="btn-confirm" @click="confirmPurchasesAndGeneratePlan">
                    {{ purchasesConfirmed ? '↻ Update Spray Plan' : '✓ Confirm Purchases & Generate Spray Plan' }}
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </section>

      <!-- My Spray Plan (Phase 2) -->
      <section class="spray-plan-section" v-if="purchasesConfirmed && sprayPlan.length > 0">
        <h2 @click="toggleSection('sprayPlan')" class="section-header" :class="{ collapsed: collapsedSections.sprayPlan }">
          <span class="section-toggle">{{ collapsedSections.sprayPlan ? '▶' : '▼' }}</span>
          📅 My Spray Plan
        </h2>
        <div v-show="!collapsedSections.sprayPlan">
          <p class="spray-plan-intro">
            Generated from your confirmed product selection. One row = one spray application day — both peronospora and oidium products can be tank-mixed in a single pass.
            Intervals: 9 days early season (April–June), 11 days mid-season, 13 days late season.
            Adjust based on weather and WBI prognosis above.
          </p>
          <div class="spray-plan-table-wrap">
            <table class="spray-plan-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Target date</th>
                  <th>🍂 Peronospora product</th>
                  <th>🌬 Oidium product</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(event, idx) in sprayPlan"
                  :key="idx"
                  :class="{
                    'plan-past': event.isPast,
                    'plan-next': event.isNext,
                    'plan-warning': (event.peron && event.peron.ruleWarning) || (event.oidium && event.oidium.ruleWarning)
                  }"
                >
                  <td class="plan-num">{{ idx + 1 }}</td>
                  <td class="plan-date">
                    {{ event.targetDate }}
                    <span v-if="event.isNext" class="next-badge">NEXT</span>
                  </td>
                  <!-- Peronospora cell -->
                  <td class="plan-disease-cell">
                    <template v-if="event.peron">
                      <div class="plan-product-name">{{ event.peron.productName }}</div>
                      <div class="plan-substance">{{ event.peron.activeSubstance }}</div>
                      <div class="plan-cell-meta">
                        <span class="frac-code-badge">FRAC {{ event.peron.fracCode }}</span>
                        <span v-if="event.peron.qtyMl" class="plan-qty-inline">{{ event.peron.qtyMl }} mL</span>
                        <span v-else class="qty-unknown">Check label</span>
                      </div>
                      <div class="plan-rule-text">{{ event.peron.ruleNote }}</div>
                    </template>
                    <span v-else class="plan-no-product">— not selected</span>
                  </td>
                  <!-- Oidium cell -->
                  <td class="plan-disease-cell">
                    <template v-if="event.oidium">
                      <div class="plan-product-name">{{ event.oidium.productName }}</div>
                      <div class="plan-substance">{{ event.oidium.activeSubstance }}</div>
                      <div class="plan-cell-meta">
                        <span class="frac-code-badge">FRAC {{ event.oidium.fracCode }}</span>
                        <span v-if="event.oidium.qtyMl" class="plan-qty-inline">{{ event.oidium.qtyMl }} mL</span>
                        <span v-else class="qty-unknown">Check label</span>
                      </div>
                      <div class="plan-rule-text">{{ event.oidium.ruleNote }}</div>
                    </template>
                    <span v-else class="plan-no-product">— not selected</span>
                  </td>
                  <td>
                    <button class="btn-log-spray" @click="prefillSprayDiary(event)" title="Pre-fill spray diary">📝</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="spray-plan-notes">
            <strong>Key FRAC rules encoded in this plan:</strong>
            <ul>
              <li>Peronospora: CAA (FRAC 40) max 3–4 applications/season — <em>FRAC CAA Working Group 2024</em></li>
              <li>Peronospora: Phenylamide (FRAC 4) max 4/season, max 2 consecutive, always in mixture — <em>FRAC Phenylamide Expert Forum 2020</em></li>
              <li>Oidium: DMI/triazole (FRAC 3) max 50% of sprays, max 3 consecutive — <em>FRAC SBI Working Group 2025</em></li>
              <li>Multisite contacts (FRAC M1, M2, M3, M4) have no FRAC application limit and form the backbone of every program</li>
            </ul>
          </div>
        </div>
      </section>

      <!-- Dosage Calculator -->
      <section class="dosage-calculator-section">
        <h2 @click="toggleSection('dosageCalculator')" class="section-header" :class="{ collapsed: collapsedSections.dosageCalculator }">
          <span class="section-toggle">{{ collapsedSections.dosageCalculator ? '▶' : '▼' }}</span>
          ⚗️ Fungicide Dosage Calculator
        </h2>
        <div v-show="!collapsedSections.dosageCalculator" class="calculator-container">
          <div class="calc-tabs">
            <button 
              @click="calcMethod = 'leafwall'" 
              :class="{ active: calcMethod === 'leafwall' }"
              class="calc-tab"
            >
              📏 Leaf Wall Size
            </button>
            <button 
              @click="calcMethod = 'growthstage'" 
              :class="{ active: calcMethod === 'growthstage' }"
              class="calc-tab"
            >
              🌱 Growth Stage
            </button>
          </div>

          <!-- Method 1: Leaf Wall Size -->
          <div v-if="calcMethod === 'leafwall'" class="calc-method">
            <h3>Method 1: Leaf Wall Coverage</h3>
            <p class="calc-description">Based on leaf wall area (more precise for varying vine sizes)</p>
            <div class="calc-form">
              <div class="calc-input-group">
                <label>Fungicide Concentration (ml/100L):</label>
                <input type="number" v-model.number="calc.concentration" step="0.1" />
              </div>
              <div class="calc-input-group">
                <label>Leaf Wall Area (m²):</label>
                <input type="number" v-model.number="calc.leafWallArea" step="1" />
                <small>Typical: 10-15 m²/are at full canopy</small>
              </div>
              <div class="calc-input-group">
                <label>Application Volume per m² (L/m²):</label>
                <input type="number" v-model.number="calc.volumePerM2" step="0.01" value="0.4" />
                <small>Standard: 0.3-0.5 L/m²</small>
              </div>
            </div>
            <div class="calc-result">
              <div class="result-item">
                <span class="result-label">Total Spray Volume:</span>
                <span class="result-value">{{ (calc.leafWallArea * calc.volumePerM2).toFixed(2) }} L</span>
              </div>
              <div class="result-item highlight">
                <span class="result-label">Fungicide Required:</span>
                <span class="result-value">{{ (calc.concentration * (calc.leafWallArea * calc.volumePerM2) / 100).toFixed(2) }} ml</span>
              </div>
            </div>
          </div>

          <!-- Method 2: Growth Stage -->
          <div v-if="calcMethod === 'growthstage'" class="calc-method">
            <h3>Method 2: Growth Stage (BBCH) Based</h3>
            <p class="calc-description">Based on vineyard size and growth stage (standard wine industry method)</p>
            <div class="calc-form">
              <div class="calc-input-group">
                <label>Fungicide Base Dosage (mL/ha):</label>
                <input type="number" v-model.number="calc.baseDosage" step="1" />
                <small>Typical: 500–2000 mL/ha. Check product label.</small>
              </div>
              <div class="calc-input-group">
                <label>Vineyard Size (ares):</label>
                <input type="number" v-model.number="calc.vineyardSize" step="0.1" />
              </div>
              <div class="calc-input-group">
                <label>Growth Stage (BBCH):</label>
                <select v-model.number="calc.bbch">
                  <option v-for="s in bbchStages" :key="s.value" :value="s.value">BBCH {{ s.range }} ({{ s.label }})</option>
                </select>
              </div>
            </div>
            <div class="calc-result">
              <div class="result-item">
                <span class="result-label">Standard Spray Volume (400 L/ha):</span>
                <span class="result-value">{{ (calc.vineyardSize * 4).toFixed(2) }} L</span>
              </div>
              <div class="result-item">
                <span class="result-label">Dosage Adjustment Factor:</span>
                <span class="result-value">{{ calc.bbch >= 81 ? '0.80x (Post-veraison)' : '1.00x (Standard)' }}</span>
              </div>
              <div class="result-item highlight">
                <span class="result-label">Fungicide Required:</span>
                <span class="result-value">{{ (calc.baseDosage * calc.vineyardSize / 100 * (calc.bbch >= 81 ? 0.8 : 1.0)).toFixed(2) }} mL</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Admin: Data Sync -->
      <section class="data-sync-section">
        <h2 @click="toggleSection('dataSync')" class="section-header" :class="{ collapsed: collapsedSections.dataSync }">
          <span class="section-toggle">{{ collapsedSections.dataSync ? '▶' : '▼' }}</span>
          🔧 Data Sync (Admin)
        </h2>
        <div v-show="!collapsedSections.dataSync" class="data-sync-container">

          <!-- BVL PSM-API sync -->
          <div class="sync-card">
            <div class="sync-card-header">
              <span class="sync-icon">🇩🇪</span>
              <div>
                <strong>BVL PSM-Register (Germany)</strong>
                <p class="sync-desc">Loads German product-level authorisations (Zulassungsnummern) from a BVL PSM-Register public API.</p>
              </div>
            </div>
            <div class="sync-status" v-if="syncStatus">
              <span class="sync-label">Last sync:</span>
              <span>{{ syncStatus.lastBvlSync || 'never' }}</span>
              <span v-if="syncStatus.lastBvlSyncResult && syncStatus.lastBvlSyncResult !== 'N/A'" class="sync-result" :class="{ 'sync-error': syncStatus.lastBvlSyncResult.startsWith('ERROR') }">
                {{ syncStatus.lastBvlSyncResult }}
              </span>
            </div>
            <p class="sync-desc" style="margin-bottom:10px">
              Fetches German product authorisations directly from the
              <a href="https://psm-api.bvl.bund.de/" target="_blank" rel="noopener">BVL PSM-API</a>. Runs automatically on the 1st of each month.
            </p>
            <button
              @click="triggerBvlSync"
              :disabled="syncingBvl"
              class="btn-sync"
            >
              {{ syncingBvl ? 'Syncing…' : 'Sync Now' }}
            </button>
            <p v-if="bvlSyncMessage" class="sync-feedback" :class="{ 'sync-feedback-error': bvlSyncMessage.startsWith('Error') }">{{ bvlSyncMessage }}</p>
          </div>

          <!-- Product counts -->
          <div v-if="syncStatus && syncStatus.products" class="sync-stats">
            <div class="sync-stat">
              <span class="stat-num">{{ syncStatus.products.total }}</span>
              <span class="stat-label">Total products</span>
            </div>
            <div class="sync-stat">
              <span class="stat-num">{{ syncStatus.products.withBvlVerification }}</span>
              <span class="stat-label">BVL-verified</span>
            </div>
          </div>

        </div>
      </section>

      <!-- External Resources -->
      <section class="external-resources-section">
        <h2 @click="toggleSection('resources')" class="section-header" :class="{ collapsed: collapsedSections.resources }">
          <span class="section-toggle">{{ collapsedSections.resources ? '▶' : '▼' }}</span>
          📚 Helpful Resources
        </h2>
        <div v-show="!collapsedSections.resources" class="resources-grid">
          <a href="https://www.vitimeteo-bw.de/" target="_blank" class="resource-link" title="Weather-based disease management tool for German viticulture">
            <span class="resource-icon">🌡️</span>
            <span class="resource-name">VitiMeteo-BW</span>
            <span class="resource-desc">Weather-based disease forecasting</span>
          </a>
          <a href="https://www.wbi.landwirtschaft-bw.de/" target="_blank" class="resource-link" title="Baden-Württemberg agriculture and viticulture information">
            <span class="resource-icon">🍇</span>
            <span class="resource-name">WBI Landwirtschaft</span>
            <span class="resource-desc">Regional viticulture guidelines</span>
          </a>
          <a href="https://www.dlr.de/de/unsere-aufgaben/pflanzliche-erzeugung/rebschutz" target="_blank" class="resource-link" title="State research and advisory center for viticulture">
            <span class="resource-icon">🔬</span>
            <span class="resource-name">DLR Rebschutz</span>
            <span class="resource-desc">Grapevine protection research & advisory</span>
          </a>
          <a href="https://www.lfl.bayern.de/rebschutz" target="_blank" class="resource-link" title="Bavarian State Research Center for Agriculture viticulture">
            <span class="resource-icon">📋</span>
            <span class="resource-name">LfL Rebschutz</span>
            <span class="resource-desc">Disease management recommendations</span>
          </a>
        </div>
      </section>

      <!-- Error/Loading States -->
      <div v-if="error" class="error-message">
        ⚠️ {{ error }}
      </div>

      <div v-if="loading" class="loading">
        Loading data...
      </div>
    </main>

    <footer class="footer">
      <p>Weinbot v0.1.0 • Weather: Meteoblue • Disease Models: LVWO/AWRI</p>
    </footer>
  </div>
</template>

<script>
import axios from 'axios'

// FRAC rotation slot definitions — based on official FRAC Working Group recommendations
const PERON_SLOTS = [
  {
    id: 'P1', label: 'Slot P1',
    name: 'Multisite contact backbone',
    fracCodes: ['M1', 'M3', 'M4'],
    optional: false,
    rule: 'No FRAC resistance limit. Forms the backbone of every program — use on every spray not covered by other slots. Copper (M1), dithiocarbamates (M3), phthalimides (M4).',
    warning: null,
    ruleShort: 'No FRAC application limit (multisite contact)'
  },
  {
    id: 'P2', label: 'Slot P2',
    name: 'CAA fungicide — systemic, preventive',
    fracCodes: ['40'],
    optional: false,
    rule: 'FRAC CAA WG 2024: max 3–4 applications/season. Always apply preventively before expected rain. Always alternate with multisite contact partner.',
    warning: null,
    ruleShort: 'CAA WG: max 3–4/season, preventive before rain'
  },
  {
    id: 'P3', label: 'Slot P3',
    name: 'Phenylamide — systemic (optional, high pressure)',
    fracCodes: ['4'],
    optional: true,
    rule: 'FRAC Phenylamide EF 2020: max 2–4 applications/season, never more than 2 consecutive, always in mixture with a partner from a different FRAC group. Use only under high disease pressure.',
    warning: 'Resistance to FRAC 4 is documented in P. viticola populations in Germany. Use only when high disease pressure justifies it and always in mixture with a non-FRAC-4 partner.',
    ruleShort: 'Phenylamide EF: max 4/season, max 2 consecutive, always in mixture'
  },
  {
    id: 'P4', label: 'Slot P4',
    name: 'Phosphonate — systemic, late season (optional)',
    fracCodes: ['33'],
    optional: true,
    rule: 'No FRAC resistance limit. Systemic with curative activity. Useful as a late-season application after the CAA limit is reached, or following heavy infection periods.',
    warning: null,
    ruleShort: 'No FRAC application limit — systemic curative'
  }
]

const OIDIUM_SLOTS = [
  {
    id: 'O1', label: 'Slot O1',
    name: 'Sulfur backbone',
    fracCodes: ['M2'],
    optional: false,
    rule: 'No FRAC resistance limit. Backbone for powdery mildew throughout the entire season. Do not apply above 28°C (phytotoxic risk, especially under high UV).',
    warning: null,
    ruleShort: 'No FRAC application limit — do not spray above 28°C'
  },
  {
    id: 'O2', label: 'Slot O2',
    name: 'DMI / triazole — sterol biosynthesis inhibitor',
    fracCodes: ['3'],
    optional: false,
    rule: 'FRAC SBI WG 2025: limit to max 50% of total oidium sprays per season. Max 3 consecutive applications of any SBI. Always alternate or mix with a non-SBI fungicide.',
    warning: null,
    ruleShort: 'SBI WG: max 50% of sprays, max 3 consecutive applications'
  },
  {
    id: 'O3', label: 'Slot O3',
    name: 'Amine / morpholine — SBI group (optional)',
    fracCodes: ['5'],
    optional: true,
    rule: 'FRAC SBI WG: cross-resistant with DMI (both belong to the SBI group). Use to replace one DMI application mid-season for rotation. Counts toward the SBI 50% total.',
    warning: null,
    ruleShort: 'SBI cross-resistance group — counts with DMI toward the 50% limit'
  },
  {
    id: 'O4', label: 'Slot O4',
    name: 'Quinoline — key rotation partner (optional)',
    fracCodes: ['13'],
    optional: true,
    rule: 'Key rotation partner for DMIs. Completely different mode of action — breaks SBI selection pressure. Registered specifically for powdery mildew. No cross-resistance with SBI group.',
    warning: null,
    ruleShort: 'FRAC 13 — no cross-resistance with DMI or amines'
  }
]

// Canonical BBCH growth stage definitions — used by the spray calculator dropdown
// and bbchLabel() to map any BBCH code to a human-readable description.
const BBCH_STAGES = [
  { value: 0,  range: '00–09', label: 'Pre-budburst' },
  { value: 10, range: '10–19', label: 'Budburst' },
  { value: 25, range: '25–29', label: '5–9 leaves' },
  { value: 35, range: '35–39', label: 'Visible flower clusters' },
  { value: 45, range: '45–49', label: 'Bloom' },
  { value: 55, range: '55–59', label: 'Fruitset' },
  { value: 65, range: '65–69', label: 'Berries pea-sized' },
  { value: 75, range: '75–79', label: 'Véraison beginning' },
  { value: 81, range: '80–85', label: 'Post-véraison' },
  { value: 89, range: '89',    label: 'Harvest' },
]

export default {
  name: 'App',
  data() {
    return {
      bbchStages: BBCH_STAGES,
      vineyard: null,
      currentWeather: null,
      risks: [],
      wbiPrognosis: {
        peronospora: null,
        oidium: null
      },
      incubationEvents: [],
      latestPheno: null,
      rainfallSummary: null,

      sprayRecommendation: null,
      growthStage: null,
      recentSprays: [],
      fungicides: [],
      diseases: [],
      fungicidesByDisease: {},
      loadingFungicides: false,
      rotationPlans: {},
      expiringApprovals: [],
      calcMethod: 'leafwall',
      calc: {
        concentration: 250,
        leafWallArea: 12,
        volumePerM2: 0.4,
        baseDosage: 1000,
        vineyardSize: 10,
        bbch: 55
      },
      currentYear: new Date().getFullYear(),
      entryMode: 'spray',
      newSpray: {
        fungicideId: '',
        diseaseId: '',
        applicationDate: '',
        growthStageBbch: '',
        temperatureC: null,
        humidityPercent: null,
        windSpeedMsec: null,
        amountFungicideAppliedLiters: null,
        notes: '',
        title: '',
        entryType: '',
        tags: ''
      },
      recordingSpray: false,
      loading: false,
      error: null,
      lastUpdate: 'Never',
      // Collapsible sections state
      syncStatus: null,
      syncingBvl: false,
      bvlSyncMessage: null,
      // Season planner
      planTargets: { peronospora: true, oidium: true },
      planSprayCount: 7,
      selectedSlots: {
        peronospora: { P1: [], P2: [], P3: [], P4: [] },
        oidium: { O1: [], O2: [], O3: [], O4: [] }
      },
      purchasesConfirmed: false,
      collapsedSections: {
        sprayRecommendation: false,
        weather: false,
        growthStage: false,
        riskAssessment: true,
        wbiPrognosis: false,
        sprayLog: false,
        seasonPlanner: false,
        sprayPlan: false,
        expiryBanner: false,
        dosageCalculator: false,
        dataSync: true,
        resources: false
      }
    }
  },
  computed: {
    /** Best-known BBCH: max of GDD-based berry stage and vitimeteo max-ever BBCH.
     *  Vitimeteo alternates between leaf (11-19) and inflorescence (53+) codes;
     *  once a ≥53 code has been seen the vine stays in the susceptibility window. */
    effectiveBbch() {
      const gddBerry = this.growthStage ? (this.growthStage.berryBbch || 0) : 0
      const phenoMax = this.latestPheno ? (this.latestPheno.maxBbchCode || 0) : 0
      return Math.max(gddBerry, phenoMax)
    },
    peronDiseaseId () {
      const d = this.diseases.find(d => d.commonName && d.commonName.toLowerCase().includes('peronospora'))
      return d ? d.id : null
    },
    oidiumDiseaseId () {
      const d = this.diseases.find(d => d.commonName && d.commonName.toLowerCase().includes('oidium'))
      return d ? d.id : null
    },
    activePeronSlots () {
      return PERON_SLOTS.filter(s => !s.optional || this.planSprayCount >= 6)
    },
    activeOidiumSlots () {
      return OIDIUM_SLOTS.filter(s => !s.optional || this.planSprayCount >= 6)
    },
    // Computed spray sequence for peronospora — respects FRAC CAA WG and Phenylamide EF limits
    peronSequence () {
      const n = this.planSprayCount
      const seq = Array(n).fill('P1')
      if (this.selectedSlots.peronospora.P2.length > 0) {
        // CAA: place at positions 1, 3, 5 (0-indexed), max 3 per FRAC CAA WG
        const caaMax = Math.min(3, Math.max(1, Math.floor(n * 0.43)))
        for (let i = 1, count = 0; i < n && count < caaMax; i += 2, count++) {
          seq[i] = 'P2'
        }
      }
      if (this.selectedSlots.peronospora.P3.length > 0) {
        // Phenylamide: mid-season, not consecutive with P2
        const midIdx = Math.floor(n * 0.57)
        const targetIdx = seq[midIdx] === 'P2' ? midIdx + 1 : midIdx
        if (targetIdx < n) seq[targetIdx] = 'P3'
      }
      if (this.selectedSlots.peronospora.P4.length > 0) {
        // Phosphonate: late season — find P1 nearest to n-2
        const lateIdx = n - 2
        let best = -1, bestDist = Infinity
        for (let j = 0; j < n; j++) {
          if (seq[j] === 'P1') {
            const d = Math.abs(j - lateIdx)
            if (d < bestDist) { bestDist = d; best = j }
          }
        }
        if (best >= 0) seq[best] = 'P4'
      }
      return seq
    },
    // Computed spray sequence for oidium — respects FRAC SBI WG limits
    oidiumSequence () {
      const n = this.planSprayCount
      const seq = Array(n).fill('O1')
      if (this.selectedSlots.oidium.O2.length > 0) {
        // DMI: max 40% of sprays, every 3rd position to stay safely under 50%
        const dmiMax = Math.min(4, Math.floor(n * 0.4))
        for (let i = 1, count = 0; i < n && count < dmiMax; i += 3, count++) {
          seq[i] = 'O2'
        }
      }
      if (this.selectedSlots.oidium.O3.length > 0) {
        // Amine: mid-season — replace the O2 slot nearest to mid-season
        // (falls back to placing directly at midIdx if no O2 exists yet)
        const midIdx = Math.floor(n * 0.5)
        let best = -1, bestDist = Infinity
        for (let j = 0; j < n; j++) {
          if (seq[j] === 'O2') {
            const d = Math.abs(j - midIdx)
            if (d < bestDist) { bestDist = d; best = j }
          }
        }
        if (best >= 0) {
          seq[best] = 'O3'
        } else {
          // No O2 slots — place at midIdx directly
          seq[midIdx] = 'O3'
        }
      }
      if (this.selectedSlots.oidium.O4.length > 0) {
        // Quinoline: late season — find O1 nearest to 70% mark
        const lateIdx = Math.floor(n * 0.7)
        let best = -1, bestDist = Infinity
        for (let j = 0; j < n; j++) {
          if (seq[j] === 'O1') {
            const d = Math.abs(j - lateIdx)
            if (d < bestDist) { bestDist = d; best = j }
          }
        }
        if (best >= 0) seq[best] = 'O4'
      }
      return seq
    },
    shoppingList () {
      const list = []
      const allProducts = Object.values(this.fungicidesByDisease).flat()
      const addSlots = (activeSlots, selectedSlots, sequence, diseaseLabel) => {
        for (const slot of activeSlots) {
          const productIds = selectedSlots[slot.id] || []
          if (productIds.length === 0) continue
          const totalUses = sequence.filter(s => s === slot.id).length
          productIds.forEach((productId, pi) => {
            const product = allProducts.find(p => p.id === productId)
            if (!product) return
            // Distribute uses across products in this slot (round-robin)
            const thisUses = pi < (totalUses % productIds.length)
              ? Math.ceil(totalUses / productIds.length)
              : Math.floor(totalUses / productIds.length)
            const qtyPerApp = (product.baseDosageMlHa && this.vineyard)
              ? Math.round(product.baseDosageMlHa * this.vineyard.sizeAres / 10000) : null
            const totalMl = qtyPerApp ? qtyPerApp * thisUses : null
            const existing = list.find(i => i.productId === product.id)
            if (existing) {
              existing.applications += thisUses
              if (existing.totalMl !== null && totalMl !== null) existing.totalMl += totalMl
            } else {
              list.push({
                productId: product.id, productName: product.name,
                activeSubstance: product.activeSubstance,
                fracCode: product.fracCode && product.fracCode !== 'UNKNOWN' ? product.fracCode : slot.fracCodes[0],
                applications: thisUses, totalMl,
                expiringApproval: (this.expiringApprovals.find(a => a.productId === product.id) || {}).bvlApprovalExpiry || null
              })
            }
          })
        }
      }
      if (this.planTargets.peronospora) {
        addSlots(this.activePeronSlots, this.selectedSlots.peronospora, this.peronSequence)
      }
      if (this.planTargets.oidium) {
        addSlots(this.activeOidiumSlots, this.selectedSlots.oidium, this.oidiumSequence)
      }
      return list
    },

    sprayPlan () {
      if (!this.purchasesConfirmed) return []
      const today = new Date()
      const vineyardAres = (this.vineyard && this.vineyard.sizeAres) || 10
      const allProducts = Object.values(this.fungicidesByDisease).flat()
      const getProduct = id => allProducts.find(p => p.id === id)
      const intervals = [0, 9, 18, 27, 37, 48, 59, 70, 83, 96, 110, 124]
      const n = this.planSprayCount

      const makeDiseaseEvent = (slotId, allSlots, selectedSlots, diseaseId, sequence, idx) => {
        const slot = allSlots.find(s => s.id === slotId)
        if (!slot) return null
        const productIds = selectedSlots[slotId] || []
        if (productIds.length === 0) return null
        const priorUses = sequence.slice(0, idx).filter(s => s === slotId).length
        const product = getProduct(productIds[priorUses % productIds.length])
        if (!product) return null
        return {
          slotId,
          productName: product.name, productId: product.id,
          diseaseId,
          activeSubstance: product.activeSubstance,
          fracCode: product.fracCode && product.fracCode !== 'UNKNOWN' ? product.fracCode : slot.fracCodes[0],
          slotName: slot.name,
          qtyMl: product.baseDosageMlHa ? Math.round(product.baseDosageMlHa * vineyardAres / 10000) : null,
          ruleNote: slot.ruleShort, ruleWarning: !!slot.warning
        }
      }

      const events = []
      for (let i = 0; i < n; i++) {
        const daysOffset = intervals[i] !== undefined ? intervals[i] : i * 10
        const targetDate = new Date(today.getTime() + daysOffset * 86400000)
        const peronEvent = this.planTargets.peronospora
          ? makeDiseaseEvent(this.peronSequence[i], PERON_SLOTS, this.selectedSlots.peronospora, this.peronDiseaseId, this.peronSequence, i)
          : null
        const oidiumEvent = this.planTargets.oidium
          ? makeDiseaseEvent(this.oidiumSequence[i], OIDIUM_SLOTS, this.selectedSlots.oidium, this.oidiumDiseaseId, this.oidiumSequence, i)
          : null
        if (peronEvent || oidiumEvent) {
          const isPast = targetDate < today
          events.push({
            idx: i,
            targetDate: targetDate.toLocaleDateString('de-DE'),
            targetDateObj: targetDate,
            peron: peronEvent, oidium: oidiumEvent,
            isPast, isNext: false
          })
        }
      }
      let nextFound = false
      events.forEach(e => {
        if (!nextFound && !e.isPast) { e.isNext = true; nextFound = true }
      })
      return events
    }
  },
  mounted() {
    this.refreshData()
  },
  methods: {
    async refreshData() {
      this.loading = true
      this.error = null
      try {
        await Promise.all([
          this.fetchVineyard(),
          this.fetchWeather(),
          this.fetchRiskAssessment(),
          this.fetchWbiPrognosis(),
          this.fetchIncubationEvents(),
          this.fetchLatestPheno(),
          this.fetchRainfallSummary(),
          this.fetchGrowthStage(),
          this.fetchFungicides(),
          this.fetchDiseases(),
          this.fetchRecentSprays()
        ])
        // After diseases are loaded, fetch fungicides for each disease
        await this.fetchFungicidesForAllDiseases()
        await Promise.all([
          this.fetchRotationPlans(),
          this.fetchExpiringApprovals()
        ])
        // Spray recommendation depends on vineyard being loaded first
        await this.fetchSprayRecommendation()
        this.checkAndFireNotification()
        this.lastUpdate = new Date().toLocaleTimeString()
        this.loadPersistedPlan()
      } catch (err) {
        this.error = `Failed to load data: ${err.message}`
        console.error(err)
      } finally {
        this.loading = false
      }
    },
    async fetchVineyard() {
      try {
        const response = await axios.get('/api/v1/vineyards')
        if (response.data && response.data.length > 0) {
          this.vineyard = response.data[0]
        }
      } catch (err) {
        console.warn('Failed to fetch vineyard:', err)
      }
    },
    async fetchWeather() {
      try {
        // First, fetch latest weather
        const response = await axios.get('/api/v1/weather/latest')
        console.log('Weather response:', response.data)
        // Backend returns a single object, not an array
        if (response.data && response.data.temperatureC !== undefined) {
          this.currentWeather = response.data
        } else if (response.data && Array.isArray(response.data) && response.data.length > 0) {
          this.currentWeather = response.data[0]
        } else {
          // If no weather data, trigger a fetch
          console.log('No weather data, triggering fetch...')
          await axios.post('/api/v1/weather/fetch?days=7')
          const retryResponse = await axios.get('/api/v1/weather/latest')
          console.log('Weather retry response:', retryResponse.data)
          if (retryResponse.data && retryResponse.data.temperatureC !== undefined) {
            this.currentWeather = retryResponse.data
          } else if (retryResponse.data && Array.isArray(retryResponse.data) && retryResponse.data.length > 0) {
            this.currentWeather = retryResponse.data[0]
          }
        }
      } catch (err) {
        console.warn('Failed to fetch weather:', err)
      }
    },
    async fetchRiskAssessment() {
      try {
        // Trigger assessment
        await axios.post('/api/v1/risk/assess')
        // Fetch latest risks
        const response = await axios.get('/api/v1/risk/latest')
        console.log('Risk response:', response.data)
        
        // Backend returns flat object: { "Oidium": {...}, "Peronospora": {...} }
        // Convert to array format for template
        if (response.data && typeof response.data === 'object') {
          // Fetch disease data to get german names
          const diseasesResponse = await axios.get('/api/v1/diseases')
          const diseaseMap = {}
          if (diseasesResponse.data && Array.isArray(diseasesResponse.data)) {
            diseasesResponse.data.forEach(d => {
              diseaseMap[d.commonName] = d
            })
          }
          
          // Transform flat object to array with disease data
          this.risks = Object.entries(response.data).map(([diseaseName, riskData]) => ({
            id: diseaseName,
            disease: diseaseMap[diseaseName] || { commonName: diseaseName, germanName: diseaseName },
            riskScore: riskData.riskScore,
            riskLevel: riskData.riskLevel,
            recommendation: riskData.recommendation,
            calculationBreakdown: riskData.calculationBreakdown,
            assessedAt: riskData.assessedAt
          }))
          console.log('Transformed risks:', this.risks)
        } else if (Array.isArray(response.data)) {
          this.risks = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch risk assessment:', err)
      }
    },
    async fetchWbiPrognosis() {
      try {
        const [perResponse, oidResponse] = await Promise.all([
          axios.get('/api/v1/wbi/prognosis/latest?disease=peronospora').catch(() => null),
          axios.get('/api/v1/wbi/prognosis/latest?disease=oidium').catch(() => null)
        ])
        
        if (perResponse?.data) {
          this.wbiPrognosis.peronospora = perResponse.data
          console.log('Peronospora prognosis:', perResponse.data)
        }
        if (oidResponse?.data) {
          this.wbiPrognosis.oidium = oidResponse.data
          console.log('Oidium prognosis:', oidResponse.data)
        }
      } catch (err) {
        console.warn('Failed to fetch WBI prognosis:', err)
      }
    },
    async fetchIncubationEvents() {
      try {
        const response = await axios.get('/api/v1/wbi/incubation/active')
        if (Array.isArray(response.data)) {
          this.incubationEvents = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch incubation events:', err)
      }
    },
    async fetchLatestPheno() {
      try {
        const response = await axios.get('/api/v1/wbi/pheno/latest')
        if (response.data) {
          this.latestPheno = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch latest pheno:', err)
      }
    },
    async fetchRainfallSummary() {
      try {
        const response = await axios.get('/api/v1/spray/rainfall-summary')
        console.log('Rainfall summary response:', response.data)
        if (response.data) {
          this.rainfallSummary = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch rainfall summary:', err)
      }
    },
    async fetchGrowthStage() {
      try {
        const response = await axios.get('/api/v1/growth-stage/current')
        if (response.data) {
          this.growthStage = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch growth stage:', err)
      }
    },
    toggleSection(sectionName) {
      this.collapsedSections[sectionName] = !this.collapsedSections[sectionName]
      if (sectionName === 'dataSync' && !this.collapsedSections.dataSync) {
        this.fetchSyncStatus()
      }
    },
    async fetchSyncStatus() {
      try {
        const response = await axios.get('/api/v1/admin/sync/status')
        this.syncStatus = response.data
      } catch (err) {
        console.warn('Failed to fetch sync status:', err)
      }
    },
    async triggerBvlSync() {
      this.syncingBvl = true
      this.bvlSyncMessage = null
      try {
        const response = await axios.post('/api/v1/admin/sync/bvl-api')
        this.bvlSyncMessage = response.data.message
        await this.fetchSyncStatus()
        await this.fetchFungicides()
        await this.fetchExpiringApprovals()
      } catch (err) {
        this.bvlSyncMessage = 'Error: ' + (err.response?.data?.message || err.message)
      } finally {
        this.syncingBvl = false
      }
    },
    formatOptimalConditions(disease) {
      const commonName = disease?.commonName || disease?.name || ''
      if (commonName.includes('Peronospora')) {
        return '10-25°C + 85%+ humidity + wetness'
      } else if (commonName.includes('Oidium')) {
        return '15-27°C + 40%+ humidity'
      }
      return 'Check thresholds'
    },
    isFutureEvent(datetimeArr) {
      if (!datetimeArr || !Array.isArray(datetimeArr)) return false
      const [year, month, day, hour = 0, minute = 0, second = 0] = datetimeArr
      return new Date(year, month - 1, day, hour, minute, second) > new Date()
    },
    formatDateTime(isoString) {
      if (!isoString) return 'N/A'
      try {
        // Handle both ISO string and array formats
        let date
        if (Array.isArray(isoString)) {
          // If it's an array [year, month, day, hour, minute, second, nanos]
          // Jackson omits trailing zeros, so arrays may be 5 elements when seconds = 0
          const [year, month, day, hour = 0, minute = 0, second = 0] = isoString
          date = new Date(year, month - 1, day, hour, minute, second)
        } else {
          // If it's an ISO string
          date = new Date(isoString)
        }
        
        if (isNaN(date.getTime())) {
          return 'Invalid date'
        }
        
        return date.toLocaleString(undefined, { 
          month: 'short', 
          day: 'numeric', 
          hour: '2-digit', 
          minute: '2-digit'
        })
      } catch (e) {
        console.warn('Error parsing date:', isoString, e)
        return 'Invalid date'
      }
    },
    /** Maps a BBCH numeric code to a short human-readable description.
     *  Fine-grained overrides handle exact vitimeteo BBCH-Code series values;
     *  everything else falls back to the canonical BBCH_STAGES coarse lookup. */
    bbchLabel(code) {
      if (!code && code !== 0) return ''
      if (code >= 11 && code <= 19) return `${code - 10} lea${code - 10 === 1 ? 'f' : 'ves'} unfolded`
      if (code === 53) return 'Inflorescence clearly visible'
      if (code === 55) return 'Individual flowers visible'
      if (code === 57) return 'Flowers separating'
      // Coarse fallback: highest BBCH_STAGES entry whose value ≤ code
      const stage = [...BBCH_STAGES].reverse().find(s => s.value <= code)
      return stage ? stage.label : `BBCH ${code}`
    },
    formatWbiDate(dateArray) {
      if (!dateArray) return 'N/A'
      try {
        // Handle array format [year, month, day]
        if (Array.isArray(dateArray)) {
          const [year, month, day] = dateArray
          const date = new Date(year, month - 1, day)
          
          if (isNaN(date.getTime())) {
            return 'Invalid date'
          }
          
          return date.toLocaleDateString(undefined, { 
            month: 'short', 
            day: 'numeric', 
            year: 'numeric'
          })
        }
        return 'Invalid date'
      } catch (e) {
        console.warn('Error parsing WBI date:', dateArray, e)
        return 'Invalid date'
      }
    },
    async fetchFungicides() {
      try {
        const response = await axios.get('/api/v1/fungicides/all')
        if (Array.isArray(response.data)) {
          this.fungicides = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch fungicides:', err)
      }
    },
    async fetchDiseases() {
      try {
        const response = await axios.get('/api/v1/diseases')
        if (Array.isArray(response.data)) {
          this.diseases = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch diseases:', err)
      }
    },
    async fetchFungicidesForAllDiseases() {
      // Fetch fungicides for each disease and store by disease ID
      this.loadingFungicides = true
      try {
        for (const disease of this.diseases) {
          try {
            const response = await axios.get(`/api/v1/fungicide-management/by-disease/${disease.id}`)
            // Extract fungicides array from the response object
            if (response.data && response.data.fungicides && Array.isArray(response.data.fungicides)) {
              this.fungicidesByDisease[disease.id] = response.data.fungicides
            } else {
              this.fungicidesByDisease[disease.id] = []
            }
          } catch (err) {
            console.warn(`Failed to fetch fungicides for disease ${disease.id}:`, err)
            this.fungicidesByDisease[disease.id] = []
          }
        }
      } finally {
        this.loadingFungicides = false
      }
    },
    async fetchRotationPlans() {
      for (const disease of this.diseases) {
        try {
          const response = await axios.get(`/api/v1/fungicide-management/rotation-plan/${disease.id}`)
          if (response.data && response.data.status === 'SUCCESS') {
            this.rotationPlans[disease.id] = response.data
          }
        } catch (err) {
          console.warn(`Failed to fetch rotation plan for disease ${disease.id}:`, err)
        }
      }
    },
    async fetchExpiringApprovals() {
      try {
        const response = await axios.get('/api/v1/fungicide-management/approvals/expiring?daysAhead=120')
        if (response.data && Array.isArray(response.data.expiringApprovals)) {
          this.expiringApprovals = response.data.expiringApprovals
        }
      } catch (err) {
        console.warn('Failed to fetch expiring approvals:', err)
      }
    },
    async fetchRecentSprays() {      try {
        const response = await axios.get('/api/v1/vineyard-logs/recent-sprays/1')
        if (response.data && response.data.sprays) {
          this.recentSprays = response.data.sprays
        }
      } catch (err) {
        console.warn('Failed to fetch recent sprays:', err)
      }
    },
    async fetchSprayRecommendation() {
      if (!this.vineyard) return
      try {
        const response = await axios.get(`/api/v1/spray/recommendation?vineyardId=${this.vineyard.id}`)
        if (response.data) {
          this.sprayRecommendation = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch spray recommendation:', err)
      }
    },
    checkAndFireNotification() {
      if (!this.sprayRecommendation || !this.sprayRecommendation.actionWithin7Days) return
      if (!('Notification' in window)) return
      if (Notification.permission === 'denied') return

      const fire = () => {
        const emoji = this.sprayRecommendation.urgency === 'URGENT' ? '🚨' : '⚠️'
        new Notification(`${emoji} Rebenbot: Spray Action Required`, {
          body: this.sprayRecommendation.explanation,
          tag: 'spray-reminder'
        })
      }

      if (Notification.permission === 'granted') {
        fire()
      } else {
        Notification.requestPermission().then(permission => {
          if (permission === 'granted') fire()
        })
      }
    },
    urgencyClass(urgency) {
      return {
        'rec-urgent': urgency === 'URGENT',
        'rec-action': urgency === 'ACTION_RECOMMENDED',
        'rec-scheduled': urgency === 'SCHEDULED',
        'rec-monitor': urgency === 'MONITOR'
      }
    },
    formatDate(dateValue) {
      if (!dateValue) return 'N/A'
      try {
        let date
        if (Array.isArray(dateValue)) {
          const [year, month, day] = dateValue
          date = new Date(year, month - 1, day)
        } else {
          date = new Date(dateValue)
        }
        if (isNaN(date.getTime())) return 'Invalid date'
        return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
      } catch (e) {
        return 'Invalid date'
      }
    },
    async recordEntry() {
      if (this.entryMode === 'spray') {
        return this.recordSpray()
      } else {
        return this.recordDiaryNote()
      }
    },
    async recordSpray() {
      if (!this.newSpray.fungicideId || !this.newSpray.diseaseId || !this.newSpray.applicationDate) {
        alert('Please fill in all required fields')
        return
      }

      this.recordingSpray = true
      try {
        const payload = {
          vineyardId: 1,
          fungicideId: Number(this.newSpray.fungicideId),
          diseaseId: Number(this.newSpray.diseaseId),
          applicationDate: this.newSpray.applicationDate,
          growthStageBbch: this.newSpray.growthStageBbch || null,
          temperatureC: this.newSpray.temperatureC,
          humidityPercent: this.newSpray.humidityPercent,
          windSpeedMsec: this.newSpray.windSpeedMsec,
          amountFungicideAppliedLiters: this.newSpray.amountFungicideAppliedLiters || null,
          notes: this.newSpray.notes
        }

        const response = await axios.post('/api/v1/vineyard-logs/record-spray', payload)
        
        if (response.data && response.data.status === 'SUCCESS') {
          alert('Spray recorded successfully!')
          // Reset form
          this.newSpray = {
            fungicideId: '',
            diseaseId: '',
            applicationDate: '',
            growthStageBbch: '',
            temperatureC: null,
            humidityPercent: null,
            windSpeedMsec: null,
            amountFungicideAppliedLiters: null,
            notes: '',
            title: '',
            entryType: '',
            tags: ''
          }
          // Refresh recent sprays
          await this.fetchRecentSprays()
        }
      } catch (err) {
        console.error('Error recording spray:', err)
        alert(`Error: ${err.response?.data?.message || err.message}`)
      } finally {
        this.recordingSpray = false
      }
    },
    async recordDiaryNote() {
      if (!this.newSpray.applicationDate || !this.newSpray.title || !this.newSpray.entryType) {
        alert('Please fill in all required fields')
        return
      }

      this.recordingSpray = true
      try {
        const payload = {
          vineyardId: 1,
          entryDate: this.newSpray.applicationDate,
          title: this.newSpray.title,
          description: this.newSpray.notes,
          entryType: this.newSpray.entryType,
          growthStageBbch: this.newSpray.growthStageBbch || null,
          tags: this.newSpray.tags
        }

        const response = await axios.post('/api/v1/vineyard-logs/create-entry', payload)
        
        if (response.data && response.data.status === 'SUCCESS') {
          alert('Diary entry created successfully!')
          // Reset form
          this.newSpray = {
            fungicideId: '',
            diseaseId: '',
            applicationDate: '',
            growthStageBbch: '',
            temperatureC: null,
            humidityPercent: null,
            windSpeedMsec: null,
            amountFungicideAppliedLiters: null,
            notes: '',
            title: '',
            entryType: '',
            tags: ''
          }
          // Refresh recent sprays
          await this.fetchRecentSprays()
        }
      } catch (err) {
        console.error('Error creating diary entry:', err)
        alert(`Error: ${err.response?.data?.message || err.message}`)
      } finally {
        this.recordingSpray = false
      }
    },

    // ===== Season Planner methods =====

    peronProductsForSlot (slot) {
      if (!this.peronDiseaseId) return []
      const products = this.fungicidesByDisease[this.peronDiseaseId] || []
      return products
        .map(p => ({
          ...p,
          fracMatched: !!(p.fracCode && p.fracCode !== 'UNKNOWN' && slot.fracCodes.includes(p.fracCode)),
          fracUnknown: !p.fracCode || p.fracCode === 'UNKNOWN'
        }))
        .filter(p => p.fracMatched || p.fracUnknown)
        .sort((a, b) => {
          if (a.fracMatched && !b.fracMatched) return -1
          if (!a.fracMatched && b.fracMatched) return 1
          if (a.baseDosageMlHa && !b.baseDosageMlHa) return -1
          if (!a.baseDosageMlHa && b.baseDosageMlHa) return 1
          return (a.name || '').localeCompare(b.name || '')
        })
    },

    oidiumProductsForSlot (slot) {
      if (!this.oidiumDiseaseId) return []
      const products = this.fungicidesByDisease[this.oidiumDiseaseId] || []
      return products
        .map(p => ({
          ...p,
          fracMatched: !!(p.fracCode && p.fracCode !== 'UNKNOWN' && slot.fracCodes.includes(p.fracCode)),
          fracUnknown: !p.fracCode || p.fracCode === 'UNKNOWN'
        }))
        .filter(p => p.fracMatched || p.fracUnknown)
        .sort((a, b) => {
          if (a.fracMatched && !b.fracMatched) return -1
          if (!a.fracMatched && b.fracMatched) return 1
          if (a.baseDosageMlHa && !b.baseDosageMlHa) return -1
          if (!a.baseDosageMlHa && b.baseDosageMlHa) return 1
          return (a.name || '').localeCompare(b.name || '')
        })
    },

    toggleSlotProduct (disease, slotId, productId) {
      const arr = this.selectedSlots[disease][slotId]
      const idx = arr.indexOf(productId)
      if (idx >= 0) {
        arr.splice(idx, 1)
      } else {
        arr.push(productId)
      }
    },

    getProductName (productId) {
      const all = Object.values(this.fungicidesByDisease).flat()
      const p = all.find(p => p.id === productId)
      return p ? p.name : productId
    },

    productUsesInSlot (totalUses, totalProducts, productIndex) {
      if (totalProducts === 0) return 0
      return productIndex < (totalUses % totalProducts)
        ? Math.ceil(totalUses / totalProducts)
        : Math.floor(totalUses / totalProducts)
    },

    calcBuyQtyForProductInSlot (productId, productIndex, totalProducts, totalUses) {
      const uses = this.productUsesInSlot(totalUses, totalProducts, productIndex)
      const all = Object.values(this.fungicidesByDisease).flat()
      const product = all.find(p => p.id === productId)
      if (!product || !product.baseDosageMlHa || !this.vineyard) return '—'
      const qtyPerApp = Math.round(product.baseDosageMlHa * this.vineyard.sizeAres / 10000)
      const total = qtyPerApp * uses
      return total >= 1000 ? `${(total / 1000).toFixed(2)} L` : `${total} mL`
    },

    calcBuyQty (productId, useCount) {
      const allProducts = Object.values(this.fungicidesByDisease).flat()
      const product = allProducts.find(p => p.id === productId)
      if (!product || !product.baseDosageMlHa || !this.vineyard) return '—'
      const qtyPerApp = Math.round(product.baseDosageMlHa * this.vineyard.sizeAres / 10000)
      const total = qtyPerApp * useCount
      return total >= 1000 ? `${(total / 1000).toFixed(2)} L` : `${total} mL`
    },

    confirmPurchasesAndGeneratePlan () {
      this.purchasesConfirmed = true
      try {
        localStorage.setItem('rebenbot_selectedSlots', JSON.stringify(this.selectedSlots))
        localStorage.setItem('rebenbot_planConfig', JSON.stringify({
          planTargets: this.planTargets,
          planSprayCount: this.planSprayCount
        }))
      } catch (e) { /* ignore storage errors */ }
      this.collapsedSections.sprayPlan = false
      this.$nextTick(() => {
        const el = document.querySelector('.spray-plan-section')
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    },

    loadPersistedPlan () {
      try {
        const slotsJson = localStorage.getItem('rebenbot_selectedSlots')
        const configJson = localStorage.getItem('rebenbot_planConfig')
        if (slotsJson) {
          const saved = JSON.parse(slotsJson)
          // Support old single-value format as well as new array format
          const toArray = v => Array.isArray(v) ? v : (v ? [v] : [])
          if (saved.peronospora) {
            for (const k of Object.keys(saved.peronospora)) {
              if (this.selectedSlots.peronospora[k] !== undefined) {
                this.selectedSlots.peronospora[k] = toArray(saved.peronospora[k])
              }
            }
          }
          if (saved.oidium) {
            for (const k of Object.keys(saved.oidium)) {
              if (this.selectedSlots.oidium[k] !== undefined) {
                this.selectedSlots.oidium[k] = toArray(saved.oidium[k])
              }
            }
          }
        }
        if (configJson) {
          const c = JSON.parse(configJson)
          if (c.planTargets) Object.assign(this.planTargets, c.planTargets)
          if (c.planSprayCount) this.planSprayCount = c.planSprayCount
        }
        if (slotsJson) {
          this.purchasesConfirmed = true
        }
      } catch (e) { /* ignore */ }
    },

    prefillSprayDiary (event) {
      this.entryMode = 'spray'
      // Pre-fill with peronospora product if available, else oidium
      const primary = event.peron || event.oidium
      this.newSpray.fungicideId = (primary && primary.productId) || ''
      this.newSpray.diseaseId = (primary && primary.diseaseId) || ''
      const now = new Date()
      this.newSpray.applicationDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
      this.collapsedSections.sprayLog = false
      this.$nextTick(() => {
        const el = document.querySelector('.spray-diary-section')
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    }
  }
}
</script>

<style scoped>
.app {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  flex-direction: column;
}

.header {
  background: rgba(255, 255, 255, 0.95);
  padding: 2rem;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content h1 {
  font-size: 2rem;
  color: #667eea;
  margin-bottom: 0.5rem;
}

.header-content p {
  color: #666;
  font-size: 0.9rem;
}

.btn-refresh {
  background: #667eea;
  color: white;
  border: none;
  padding: 0.7rem 1.5rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s ease;
}

.btn-refresh:hover:not(:disabled) {
  background: #764ba2;
  transform: translateY(-2px);
}

.btn-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.container {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 2rem;
}

section {
  margin-bottom: 2rem;
  background: white;
  border-radius: 10px;
  padding: 2rem;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

h2 {
  color: #667eea;
  margin-bottom: 1.5rem;
  font-size: 1.5rem;
}

.status-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 2rem;
}

.status-item {
  display: flex;
  flex-direction: column;
}

.status-item .label {
  color: #999;
  font-size: 0.85rem;
  margin-bottom: 0.5rem;
}

.status-item .value {
  font-size: 1.2rem;
  font-weight: 600;
  color: #333;
}

.weather-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 1.5rem;
}

.weather-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1.5rem;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 4px 10px rgba(102, 126, 234, 0.2);
}

.weather-icon {
  font-size: 2rem;
  display: block;
  margin-bottom: 0.5rem;
}

.weather-label {
  display: block;
  font-size: 0.85rem;
  opacity: 0.9;
  margin-bottom: 0.5rem;
}

.weather-value {
  display: block;
  font-size: 1.5rem;
  font-weight: 600;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
}

.risk-card {
  border-left: 5px solid #ccc;
  padding: 1.5rem;
  border-radius: 8px;
  background: #f9f9f9;
}

.risk-card.risk-critical {
  border-left-color: #ff4444;
  background: #fff5f5;
}

.risk-card.risk-high {
  border-left-color: #ff9800;
  background: #fff8f0;
}

.risk-card.risk-medium {
  border-left-color: #ffc107;
  background: #fffef0;
}

.risk-card.risk-low {
  border-left-color: #4caf50;
  background: #f0f8f0;
}

.risk-card.risk-none {
  border-left-color: #2196f3;
  background: #f0f7ff;
}

.risk-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.risk-header h3 {
  margin: 0;
  font-size: 1.1rem;
}

.risk-badge {
  padding: 0.4rem 0.8rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
}

.risk-critical .risk-badge {
  background: #ff4444;
  color: white;
}

.risk-high .risk-badge {
  background: #ff9800;
  color: white;
}

.risk-medium .risk-badge {
  background: #ffc107;
  color: #333;
}

.risk-low .risk-badge {
  background: #4caf50;
  color: white;
}

.risk-none .risk-badge {
  background: #2196f3;
  color: white;
}

.risk-score-bar {
  height: 8px;
  background: #eee;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 1rem;
}

.risk-bar {
  height: 100%;
  background: linear-gradient(90deg, #4caf50 0%, #ffc107 50%, #ff4444 100%);
  transition: width 0.3s ease;
}

.risk-details p {
  margin: 0.5rem 0;
  font-size: 0.9rem;
  color: #555;
}

.risk-recommendation {
  font-style: italic;
  color: #666;
  margin-top: 1rem;
}

/* WBI Prognosis Section */
.wbi-section {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05), rgba(118, 75, 162, 0.05));
  border-top: 3px solid #667eea;
}

.wbi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1.5rem;
}

.wbi-card {
  border: 2px solid #667eea;
  border-radius: 8px;
  padding: 1.5rem;
  background: white;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.1);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.wbi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.wbi-prognosis-card {
  position: relative;
}

.wbi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #eee;
}

.wbi-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #333;
}

.wbi-badge {
  padding: 0.4rem 0.8rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
}

.wbi-badge.wbi-risk-infection_risk {
  background: #ff4444;
  color: white;
}

.wbi-badge.wbi-risk-high {
  background: #ff9800;
  color: white;
}

.wbi-badge.wbi-risk-low {
  background: #ffc107;
  color: #333;
}

.wbi-badge.wbi-risk-no_infection {
  background: #4caf50;
  color: white;
}

.wbi-score-bar {
  height: 10px;
  background: #e0e0e0;
  border-radius: 5px;
  overflow: hidden;
  margin-bottom: 1.2rem;
}

.wbi-bar {
  height: 100%;
  background: linear-gradient(90deg, #4caf50 0%, #ffc107 50%, #ff4444 100%);
  transition: width 0.3s ease;
}

.wbi-details p {
  margin: 0.6rem 0;
  font-size: 0.95rem;
  color: #555;
}

.wbi-score {
  font-weight: 600;
  color: #333;
}

.wbi-row {
  margin: 0.4rem 0;
  font-size: 0.9rem;
  color: #444;
}

.wbi-incubation-count {
  font-weight: 600;
  color: #667eea;
}

.wbi-spray-deadline {
  font-weight: 600;
  color: #e53935;
  background: #fff3e0;
  padding: 0.3rem 0.5rem;
  border-radius: 4px;
  border-left: 3px solid #e53935;
}

.wbi-incubation-bars {
  margin: 0.6rem 0;
}

.incub-bar-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0.3rem 0;
  font-size: 0.82rem;
}

.incub-bar-label {
  width: 130px;
  flex-shrink: 0;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.incub-forecast-badge {
  display: inline-block;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: #e65100;
  background: #fff3e0;
  border: 1px solid #ffb74d;
  border-radius: 3px;
  padding: 0 3px;
  margin-left: 3px;
  vertical-align: middle;
}

.incub-bar-track {
  flex: 1;
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
}

.incub-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #4caf50 0%, #ff9800 70%, #f44336 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.incub-bar-fill.incub-bar-complete {
  background: #f44336;
}

.incub-bar-pct {
  width: 34px;
  text-align: right;
  color: #555;
  font-weight: 600;
}

.wbi-forecast-date {
  color: #999;
  font-size: 0.85rem;
  margin-top: 0.8rem;
  padding-top: 0.8rem;
  border-top: 1px solid #eee;
}

/* Growth Stage Styles */
.growth-stage-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border-top: 3px solid #4caf50;
}

.growth-stage-section h2 {
  margin: 0 0 1.25rem 0;
  font-size: 1.3rem;
  color: #1a1a1a;
}

.growth-stage-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* VitiMeteo primary block */
.gs-vitimeteo-block {
  background: #f1f8e9;
  border: 1px solid #c5e1a5;
  border-radius: 8px;
  padding: 1rem 1.2rem;
}

.gs-source-line {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.6rem;
}

.gs-source-badge {
  background: #4caf50;
  color: white;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  padding: 0.15rem 0.5rem;
  text-transform: uppercase;
}

.gs-source-date {
  font-size: 0.8rem;
  color: #555;
}

.gs-primary-stage {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.7rem;
}

.gs-bbch-badge {
  background: #2e7d32;
  color: white;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 700;
  padding: 0.25rem 0.75rem;
  white-space: nowrap;
}

.gs-stage-label {
  font-size: 1rem;
  font-weight: 500;
  color: #1a1a1a;
}

.gs-metrics-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.gs-metric {
  background: white;
  border: 1px solid #c5e1a5;
  border-radius: 20px;
  font-size: 0.85rem;
  color: #2e7d32;
  font-weight: 500;
  padding: 0.2rem 0.75rem;
  white-space: nowrap;
}

/* Inflorescence / susceptibility status */
.gs-infl-block {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  border-radius: 8px;
  padding: 0.8rem 1rem;
}

.gs-infl-active {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
}

.gs-infl-inactive {
  background: #f5f5f5;
  border: 1px solid #e0e0e0;
}

.gs-infl-icon {
  font-size: 1.4rem;
  line-height: 1.2;
  flex-shrink: 0;
}

.gs-infl-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #1a1a1a;
}

.gs-infl-title.muted {
  font-weight: 400;
  color: #777;
}

.gs-oidium-warning {
  margin-top: 0.3rem;
  font-size: 0.85rem;
  color: #e65100;
  font-weight: 600;
}

.gs-oidium-clear {
  margin-top: 0.3rem;
  font-size: 0.85rem;
  color: #2e7d32;
  font-weight: 500;
}

/* Local GDD reference line */
.gs-gdd-row {
  font-size: 0.8rem;
  color: #888;
  padding: 0.3rem 0.2rem;
  cursor: help;
  border-top: 1px solid #e0e0e0;
}

.error-message {
  background: #ffebee;
  color: #c62828;
  padding: 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.loading {
  text-align: center;
  color: white;
  padding: 2rem;
  font-size: 1.1rem;
}

.footer {
  background: rgba(0, 0, 0, 0.1);
  color: white;
  text-align: center;
  padding: 1.5rem;
  margin-top: auto;
  font-size: 0.9rem;
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
    gap: 1rem;
  }

  .container {
    padding: 1rem;
  }

  section {
    padding: 1.5rem;
  }

  .weather-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .fungicide-cards {
    grid-template-columns: 1fr;
  }
}

/* Tooltip enhancements */
[title] {
  cursor: help;
  border-bottom: 1px dotted rgba(255, 255, 255, 0.3);
}

.risk-score[title],
.risk-optimal[title],
.risk-badge[title] {
  transition: opacity 0.2s ease;
}

.risk-score[title]:hover,
.risk-optimal[title]:hover,
.risk-badge[title]:hover {
  opacity: 0.8;
}

/* Spray Diary Styles */
.spray-diary-section {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, #e8eaf6 0%, #e3f2fd 100%);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin: 20px 0;
}

.spray-diary-section h2 {
  margin-top: 0;
  margin-bottom: 20px;
  color: #1a237e;
  font-size: 20px;
}

.diary-container {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}

.log-spray-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
  border: 2px solid #3f51b5;
}

.log-spray-card h3 {
  margin-top: 0;
  color: #3f51b5;
  margin-bottom: 16px;
}

.spray-form {
  display: grid;
  gap: 12px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group.full-width {
  grid-column: 1 / -1;
}

.form-group label {
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
  font-size: 13px;
}

.form-group input,
.form-group select,
.form-group textarea {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
  background: #fafafa;
}

.form-group textarea {
  min-height: 80px;
  resize: vertical;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #3f51b5;
  background: white;
  box-shadow: 0 0 0 2px rgba(63, 81, 181, 0.1);
}

.btn-submit {
  grid-column: 1 / -1;
  padding: 10px 20px;
  background: linear-gradient(135deg, #3f51b5 0%, #303f9f 100%);
  color: white;
  border: none;
  border-radius: 4px;
  font-weight: 600;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s ease;
  margin-top: 8px;
}

.btn-submit:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(63, 81, 181, 0.4);
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.recent-sprays-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
  border: 2px solid #4caf50;
}

.recent-sprays-card h3 {
  margin-top: 0;
  color: #2e7d32;
  margin-bottom: 16px;
}

.sprays-list {
  display: grid;
  gap: 12px;
}

.spray-item {
  background: #f5f5f5;
  border-left: 4px solid #4caf50;
  padding: 12px;
  border-radius: 4px;
  display: grid;
  gap: 8px;
}

.spray-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.spray-fungicide {
  color: #1565c0;
  font-size: 14px;
}

.spray-date {
  color: #666;
  font-size: 12px;
}

.spray-info {
  display: flex;
  gap: 12px;
  font-size: 13px;
}

.info-label {
  font-weight: 600;
  color: #555;
  min-width: 90px;
}

.info-value {
  color: #333;
}

.no-sprays {
  background: #f5f5f5;
  border: 2px dashed #ddd;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  color: #999;
}

@media (max-width: 768px) {
  .diary-container {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}

/* External Resources Section */
.external-resources-section {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, #f3e5f5 0%, #ede7f6 100%);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin: 20px 0;
}

.external-resources-section h2 {
  margin-top: 0;
  margin-bottom: 20px;
  color: #512da8;
  font-size: 20px;
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.resource-link {
  background: white;
  border: 2px solid #7e57c2;
  border-radius: 8px;
  padding: 16px;
  text-decoration: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  transition: all 0.3s ease;
  cursor: pointer;
}

.resource-link:hover {
  border-color: #512da8;
  box-shadow: 0 4px 12px rgba(123, 31, 162, 0.2);
  transform: translateY(-2px);
}

.resource-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.resource-name {
  font-weight: 600;
  color: #512da8;
  font-size: 14px;
  margin-bottom: 6px;
}

.resource-desc {
  color: #666;
  font-size: 12px;
  line-height: 1.4;
}

@media (max-width: 768px) {
  .resources-grid {
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  }
}

/* Resistance Prevention Guidelines */
.resistance-guidelines-section {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, #fce4ec 0%, #f8bbd0 100%);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin: 20px 0;
}

.resistance-guidelines-section h2 {
  margin-top: 0;
  margin-bottom: 20px;
  color: #c2185b;
  font-size: 20px;
}

.guidelines-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
}

.guideline-box {
  background: white;
  border: 2px solid #e91e63;
  border-radius: 8px;
  padding: 16px;
}

.guideline-box h3 {
  margin-top: 0;
  margin-bottom: 12px;
  color: #c2185b;
  font-size: 16px;
}

.guidelines-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.guidelines-list li {
  margin-bottom: 10px;
  padding-left: 24px;
  position: relative;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
}

.guidelines-list li:before {
  content: "✓";
  position: absolute;
  left: 0;
  color: #e91e63;
  font-weight: bold;
}

.disease-specific {
  background: #fff3e0;
  border-color: #ff6f00;
}

.disease-specific h3 {
  color: #e65100;
}

.disease-resistance {
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ffe0b2;
  font-size: 13px;
  line-height: 1.5;
  color: #333;
}

.disease-resistance:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.disease-resistance strong {
  color: #e65100;
}

@media (max-width: 768px) {
  .guidelines-container {
    grid-template-columns: 1fr;
  }
}

/* Buying Guide Section */
.buying-guide-section {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, #e8f5e9 0%, #f1f8e9 100%);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin: 20px 0;
}

.buying-guide-section h2 {
  margin-top: 0;
  color: #2e7d32;
  font-size: 20px;
}

.buying-guide-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: 20px;
}

.disease-buying-guide h3 {
  color: #1b5e20;
  margin-top: 0;
  margin-bottom: 16px;
  border-bottom: 3px solid #4caf50;
  padding-bottom: 8px;
}

.buying-recommendation {
  background: white;
  border-left: 4px solid #4caf50;
  padding: 12px;
  margin-bottom: 12px;
  border-radius: 4px;
}

.buying-recommendation.resistance-high {
  border-left-color: #d32f2f;
}

.buying-recommendation.resistance-medium {
  border-left-color: #f57c00;
}

.product-name {
  font-weight: 600;
  color: #1b5e20;
  margin-bottom: 8px;
}

.product-substance {
  font-weight: 400;
  color: #555;
  font-size: 13px;
}

.frac-badge-inline {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  margin: 0 4px;
  background: #e8f5e9;
  color: #1b5e20;
}

.frac-badge-inline.resistance-high {
  background: #ffebee;
  color: #c62828;
}

.frac-badge-inline.resistance-medium {
  background: #fff3e0;
  color: #e65100;
}

.resistance-warning {
  color: #c62828;
  font-size: 11px;
  font-weight: 700;
}

.moa-item {
  font-style: italic;
  color: #555;
}

/* Rotation plan box */
.rotation-plan-box {
  background: #f3e5f5;
  border: 1px solid #ce93d8;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

.rotation-plan-title {
  font-weight: 700;
  color: #6a1b9a;
  font-size: 13px;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.rotation-sequence {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.rotation-step {
  display: flex;
  align-items: center;
  gap: 4px;
}

.frac-badge {
  background: #7b1fa2;
  color: white;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 700;
}

.rotation-products {
  font-size: 12px;
  color: #4a148c;
  max-width: 120px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rotation-arrow {
  color: #9c27b0;
  font-weight: 700;
}

.rotation-rule {
  font-size: 12px;
  color: #6a1b9a;
  font-style: italic;
}

/* Approval expiry warning banner */
.expiry-warning-banner {
  background: #fff8e1;
  border: 2px solid #ffb300;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  color: #5d4037;
}

.expiry-banner-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.expiry-banner-header strong {
  color: #e65100;
}

.expiry-chevron {
  font-size: 0.85rem;
  color: #e65100;
  min-width: 16px;
  text-align: right;
}

.expiry-list {
  margin: 8px 0 0 0;
  padding-left: 20px;
}

.expiry-list li {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.multi-select-hint {
  font-size: 0.8rem;
  color: #888;
  font-style: italic;
  margin: 0 0 0.75rem 0;
}

.prc-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.4rem;
  margin-bottom: 0.25rem;
}

.prc-name {
  font-weight: 600;
  font-size: 0.9rem;
  color: #333;
  flex: 1;
}

.frac-verified-badge {
  background: #e8f5e9;
  color: #2e7d32;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
}

.frac-unknown-badge {
  background: #fff3e0;
  color: #e65100;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: help;
}

.prc-sub {
  font-size: 0.8rem;
  color: #777;
  margin-bottom: 0.5rem;
}

.prc-info-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 0.8rem;
  font-size: 0.78rem;
  margin-bottom: 0.4rem;
}

.prc-info-item {
  color: #555;
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
}

.prc-info-label {
  font-weight: 600;
  color: #444;
}

.prc-info-sub {
  color: #999;
  font-size: 0.72rem;
}

.prc-info-mfg {
  color: #667eea;
  font-style: italic;
}

.prc-info-reg {
  color: #888;
  font-family: monospace;
  font-size: 0.75rem;
}

.prc-expiry {
  font-size: 0.75rem;
  color: #c62828;
  background: #ffebee;
  padding: 0.2rem 0.5rem;
  border-radius: 3px;
  margin-top: 0.3rem;
}

.prc-selected-check {
  font-size: 0.78rem;
  font-weight: 700;
  color: #2e7d32;
  margin-top: 0.3rem;
}

.product-radio-card.frac-unverified {
  border-style: dashed;
  border-color: #ffcc02;
  background: #fffdf0;
}

.product-radio-card.frac-unverified:hover {
  border-color: #ff9800;
}

.product-radio-card.frac-unverified.selected {
  border-color: #667eea;
  border-style: solid;
  background: #f0f3ff;
}

/* Combined spray plan table */
.plan-disease-cell {
  vertical-align: top;
  padding: 0.5rem 0.8rem;
  min-width: 180px;
}

.plan-product-name {
  font-weight: 600;
  font-size: 0.88rem;
  color: #333;
  margin-bottom: 0.15rem;
}

.plan-cell-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.3rem;
}

.plan-qty-inline {
  font-size: 0.8rem;
  color: #2e7d32;
  font-weight: 600;
}

.plan-rule-text {
  font-size: 0.75rem;
  color: #888;
  margin-top: 0.25rem;
  line-height: 1.3;
}

.plan-no-product {
  color: #bbb;
  font-size: 0.85rem;
  font-style: italic;
}

.expiry-note {
  display: block;
  font-size: 12px;
  color: #795548;
  font-style: italic;
  margin-top: 2px;
}


.loading-message {
  text-align: center;
  padding: 24px;
  color: #666;
  font-style: italic;
}

.no-data-message {
  text-align: center;
  padding: 24px;
  color: #d32f2f;
  font-weight: 500;
}

.no-products-message {
  text-align: center;
  padding: 12px;
  color: #999;
  font-style: italic;
  background: #f5f5f5;
  border-radius: 4px;
  margin-top: 12px;
}

/* Spray Schedule Section */
.spray-schedule-section {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin: 20px 0;
}

.spray-schedule-section h2 {
  margin-top: 0;
  color: #e65100;
  font-size: 20px;
}

.schedule-note {
  background: rgba(255, 152, 0, 0.1);
  border-left: 3px solid #ff9800;
  padding: 12px;
  margin-bottom: 16px;
  border-radius: 4px;
  color: #666;
  font-size: 13px;
}

.schedule-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.month-schedule {
  background: white;
  border-radius: 8px;
  padding: 16px;
  border-top: 4px solid #ff6f00;
}

.month-schedule h3 {
  margin-top: 0;
  color: #e65100;
  font-size: 14px;
}

.schedule-entry {
  padding: 8px 0;
  border-bottom: 1px solid #ffe0b2;
  font-size: 13px;
  line-height: 1.5;
  color: #333;
}

.schedule-entry:last-child {
  border-bottom: none;
}

/* Dosage Calculator Section */
.dosage-calculator-section {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, #f3e5f5 0%, #ede7f6 100%);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin: 20px 0;
}

.dosage-calculator-section h2 {
  margin-top: 0;
  color: #512da8;
  font-size: 20px;
}

.calculator-container {
  background: white;
  border-radius: 8px;
  padding: 20px;
}

.calc-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.calc-tab {
  padding: 12px 16px;
  background: #f5f5f5;
  border: 2px solid #ddd;
  border-radius: 6px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  color: #666;
}

.calc-tab:hover {
  border-color: #7e57c2;
  background: #f0f4ff;
}

.calc-tab.active {
  background: linear-gradient(135deg, #7e57c2 0%, #512da8 100%);
  color: white;
  border-color: #512da8;
}

.calc-method {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.calc-method h3 {
  color: #512da8;
  margin-top: 0;
}

.calc-description {
  color: #666;
  font-size: 13px;
  margin-bottom: 16px;
  font-style: italic;
}

.calc-form {
  display: grid;
  gap: 16px;
  margin-bottom: 20px;
}

.calc-input-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.calc-input-group label {
  font-weight: 600;
  color: #333;
  font-size: 13px;
}

.calc-input-group input,
.calc-input-group select {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
}

.calc-input-group input:focus,
.calc-input-group select:focus {
  outline: none;
  border-color: #7e57c2;
  box-shadow: 0 0 0 2px rgba(126, 87, 194, 0.1);
}

.calc-input-group small {
  color: #999;
  font-size: 12px;
}

.calc-result {
  background: linear-gradient(135deg, #f3e5f5 0%, #ede7f6 100%);
  border-radius: 8px;
  padding: 16px;
  display: grid;
  gap: 12px;
}

.result-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.result-item.highlight {
  background: white;
  padding: 12px;
  border-radius: 6px;
  border: 2px solid #7e57c2;
}

.result-label {
  font-weight: 600;
  color: #333;
  font-size: 13px;
}

.result-value {
  font-weight: 700;
  color: #7e57c2;
  font-size: 14px;
  background: rgba(126, 87, 194, 0.1);
  padding: 4px 12px;
  border-radius: 4px;
}

.result-item.highlight .result-value {
  font-size: 16px;
  color: #512da8;
  background: transparent;
}

/* Form Mode Toggle */
.form-mode-toggle {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  border-bottom: 2px solid #e0e0e0;
  padding-bottom: 10px;
}

.mode-btn {
  padding: 10px 20px;
  border: none;
  background: #f5f5f5;
  color: #666;
  cursor: pointer;
  font-weight: 500;
  border-radius: 4px 4px 0 0;
  transition: all 0.3s ease;
  border-bottom: 3px solid transparent;
}

.mode-btn:hover {
  background: #efefef;
}

.mode-btn.active {
  background: white;
  color: #2e7d32;
  border-bottom-color: #2e7d32;
}

/* Required asterisk */
.required {
  color: #d32f2f;
  margin-left: 2px;
}

/* Collapsible Sections */
.section-header {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  user-select: none;
  transition: color 0.3s ease;
  padding: 10px 0;
}

.section-header:hover {
  color: #667eea;
  opacity: 0.8;
}

.section-toggle {
  display: inline-block;
  font-size: 0.9em;
  transition: transform 0.3s ease;
  min-width: 20px;
}

.section-header.collapsed .section-toggle {
  transform: rotate(-90deg);
}

/* Data Sync Section */
.data-sync-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.data-sync-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 12px;
}

.sync-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
}

.sync-card-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.sync-icon {
  font-size: 1.8em;
  line-height: 1;
  flex-shrink: 0;
}

.sync-card-header strong {
  display: block;
  font-size: 1rem;
  margin-bottom: 4px;
}

.sync-desc {
  margin: 0;
  font-size: 0.85rem;
  color: #666;
  line-height: 1.4;
}

.sync-status {
  font-size: 0.82rem;
  color: #555;
  margin-bottom: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
}

.sync-label {
  font-weight: 600;
}

.sync-result {
  background: #e8f5e9;
  color: #2e7d32;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.82rem;
}

.sync-result.sync-error {
  background: #fdecea;
  color: #c62828;
}

.btn-sync {
  background: #4a7c59;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-sync:hover:not(:disabled) {
  background: #3a6147;
}

.btn-sync:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.sync-feedback {
  margin: 8px 0 0;
  font-size: 0.85rem;
  color: #2e7d32;
  background: #e8f5e9;
  padding: 6px 10px;
  border-radius: 4px;
}

.sync-feedback.sync-feedback-error {
  color: #c62828;
  background: #fdecea;
}

.sync-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.sync-stat {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 10px 18px;
  text-align: center;
  flex: 1;
  min-width: 80px;
}

.stat-num {
  display: block;
  font-size: 1.6rem;
  font-weight: 700;
  color: #4a7c59;
}

.stat-label {
  font-size: 0.78rem;
  color: #666;
}

/* ===== Season Buying Decision (Phase 1) ===== */
.season-planner-section {
  grid-column: 1 / -1;
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  margin: 20px 0;
}

.planner-config-strip {
  display: flex;
  gap: 2rem;
  flex-wrap: wrap;
  margin-bottom: 1.5rem;
  padding: 1.5rem;
  background: #f8f9ff;
  border-radius: 8px;
  border: 1px solid #e3e7ff;
}

.config-item {
  flex: 1;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.config-item label {
  font-weight: 600;
  color: #444;
  font-size: 0.95rem;
}

.disease-pill-group {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.disease-pill {
  padding: 0.5rem 1.2rem;
  border: 2px solid #ccc;
  border-radius: 20px;
  background: white;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.disease-pill.active {
  border-color: #667eea;
  background: #667eea;
  color: white;
}

.spray-count-slider {
  width: 100%;
  accent-color: #667eea;
}

.slider-hint {
  font-size: 0.8rem;
  color: #888;
}

.disease-slot-block {
  margin-bottom: 2rem;
}

.disease-slot-title {
  color: #333;
  font-size: 1.15rem;
  margin: 0 0 0.3rem 0;
}

.frac-source {
  font-size: 0.82rem;
  color: #888;
  margin: 0 0 1rem 0;
  font-style: italic;
}

.frac-slot-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.2rem;
  margin-bottom: 1rem;
  background: #fafafa;
}

.slot-meta {
  margin-bottom: 1rem;
}

.slot-badges {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 0.5rem;
}

.slot-pos-badge {
  background: #667eea;
  color: white;
  padding: 0.2rem 0.6rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 700;
}

.frac-code-badge {
  background: #e8eaf6;
  color: #3949ab;
  padding: 0.2rem 0.6rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
}

.optional-tag {
  background: #fff3e0;
  color: #e65100;
  padding: 0.2rem 0.6rem;
  border-radius: 4px;
  font-size: 0.78rem;
}

.slot-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.3rem;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.slot-uses {
  font-size: 0.82rem;
  color: #667eea;
  font-weight: 600;
}

.slot-rule-text {
  font-size: 0.85rem;
  color: #555;
  line-height: 1.5;
}

.slot-warning-text {
  font-size: 0.83rem;
  color: #c62828;
  background: #ffebee;
  padding: 0.5rem 0.8rem;
  border-radius: 4px;
  margin-top: 0.5rem;
  line-height: 1.4;
}

.slot-product-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.no-slot-products {
  color: #999;
  font-size: 0.85rem;
  font-style: italic;
}

.product-radio-card {
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  padding: 0.8rem 1rem;
  cursor: pointer;
  min-width: 160px;
  max-width: 260px;
  flex: 1;
  transition: all 0.2s;
  background: white;
  display: block;
}

.product-radio-card:hover {
  border-color: #667eea;
}

.product-radio-card.selected {
  border-color: #667eea;
  background: #f0f3ff;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.prc-name {
  font-weight: 600;
  font-size: 0.9rem;
  color: #333;
  margin-bottom: 0.2rem;
}

.prc-sub {
  font-size: 0.8rem;
  color: #777;
  margin-bottom: 0.4rem;
}

.prc-details {
  display: flex;
  gap: 0.8rem;
  font-size: 0.78rem;
  color: #667eea;
  font-weight: 600;
  flex-wrap: wrap;
}

.slot-qty-row {
  font-size: 0.88rem;
  color: #2e7d32;
  background: #e8f5e9;
  padding: 0.5rem 0.8rem;
  border-radius: 4px;
}

.planner-summary-block {
  margin-top: 2rem;
  padding: 1.5rem;
  background: #f8f9ff;
  border-radius: 8px;
  border: 2px solid #c5cae9;
}

.planner-summary-block h3 {
  margin: 0 0 1rem 0;
  color: #333;
}

.shopping-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1rem;
}

.shopping-table th {
  text-align: left;
  padding: 0.6rem 0.8rem;
  background: #667eea;
  color: white;
  font-size: 0.85rem;
}

.shopping-table td {
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid #eee;
  font-size: 0.88rem;
  vertical-align: top;
}

.shopping-table tr:last-child td {
  border-bottom: none;
}

.expiry-inline {
  font-size: 0.78rem;
  color: #e65100;
  margin-top: 0.2rem;
}

.confirm-block {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.confirmed-msg {
  color: #2e7d32;
  font-weight: 600;
  margin: 0;
}

.btn-confirm {
  background: #667eea;
  color: white;
  border: none;
  padding: 0.8rem 1.8rem;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-confirm:hover {
  background: #764ba2;
  transform: translateY(-1px);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}

/* ===== My Spray Plan (Phase 2) ===== */
.spray-plan-section {
  grid-column: 1 / -1;
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  margin: 20px 0;
}

.spray-plan-intro {
  color: #555;
  font-size: 0.9rem;
  margin-bottom: 1.5rem;
  line-height: 1.6;
  background: #f8f9ff;
  padding: 0.8rem 1rem;
  border-radius: 6px;
  border-left: 3px solid #667eea;
}

.spray-plan-table-wrap {
  overflow-x: auto;
}

.spray-plan-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.spray-plan-table th {
  text-align: left;
  padding: 0.6rem 0.8rem;
  background: #667eea;
  color: white;
  white-space: nowrap;
}

.spray-plan-table td {
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid #eee;
  vertical-align: top;
}

.plan-past td {
  color: #bbb;
  background: #fafafa;
}

tr.plan-next {
  background: #fff8e1;
}

tr.plan-next td {
  font-weight: 600;
}

.next-badge {
  background: #ff9800;
  color: white;
  padding: 0.1rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 700;
  margin-left: 0.4rem;
}

.plan-substance {
  font-size: 0.78rem;
  color: #888;
  margin-top: 0.2rem;
}

.plan-slot-name {
  font-size: 0.78rem;
  color: #667eea;
  margin-top: 0.2rem;
}

.qty-unknown {
  color: #aaa;
  font-style: italic;
}

.btn-log-spray {
  background: none;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0.3rem 0.5rem;
  cursor: pointer;
  font-size: 1rem;
}

.btn-log-spray:hover {
  background: #f0f3ff;
  border-color: #667eea;
}

.spray-plan-notes {
  margin-top: 1.5rem;
  padding: 1rem 1.5rem;
  background: #f5f5f5;
  border-radius: 6px;
  font-size: 0.85rem;
}

.spray-plan-notes ul {
  margin: 0.5rem 0 0 0;
  padding-left: 1.5rem;
}

.spray-plan-notes li {
  margin-bottom: 0.3rem;
  color: #555;
  line-height: 1.5;
}

/* ---- Spray Recommendation ---- */
.spray-rec-section {
  margin-bottom: 1.5rem;
}

.rec-urgency-badge {
  display: inline-block;
  margin-left: 0.7rem;
  padding: 0.2rem 0.7rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 700;
  vertical-align: middle;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.rec-urgent { border-left: 5px solid #e53935; }
.rec-action { border-left: 5px solid #fb8c00; }
.rec-scheduled { border-left: 5px solid #f9a825; }
.rec-monitor { border-left: 5px solid #42a5f5; }

.rec-urgency-badge.rec-urgent   { background: #e53935; color: #fff; }
.rec-urgency-badge.rec-action   { background: #fb8c00; color: #fff; }
.rec-urgency-badge.rec-scheduled { background: #f9a825; color: #333; }
.rec-urgency-badge.rec-monitor  { background: #42a5f5; color: #fff; }

.spray-rec-card {
  background: #fff;
  border-radius: 10px;
  padding: 1.2rem 1.4rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  border-left: 5px solid #ccc;
}
.spray-rec-card.rec-urgent   { border-left-color: #e53935; background: #fff5f5; }
.spray-rec-card.rec-action   { border-left-color: #fb8c00; background: #fff8f0; }
.spray-rec-card.rec-scheduled { border-left-color: #f9a825; background: #fffde7; }
.spray-rec-card.rec-monitor  { border-left-color: #42a5f5; background: #f0f8ff; }

.rec-explanation {
  font-size: 1rem;
  font-weight: 500;
  margin: 0 0 1rem 0;
  color: #333;
}

.rec-date-row {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1rem;
}

.rec-date-block {
  display: flex;
  flex-direction: column;
  background: rgba(255,255,255,0.7);
  border-radius: 8px;
  padding: 0.5rem 0.9rem;
  min-width: 120px;
}

.rec-date-label {
  font-size: 0.72rem;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.rec-date-value {
  font-size: 1rem;
  font-weight: 600;
  color: #222;
}

.rec-wbi-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  margin-bottom: 1rem;
}

.rec-wbi-chip {
  padding: 0.5rem 1rem;
  border-radius: 8px;
  font-size: 0.85rem;
  background: #eee;
  color: #333;
  flex: 1 1 200px;
}

.rec-wbi-chip.wbi-risk-infection_risk {
  background: #fff0f0;
  border: 1px solid #e53935;
  color: #b71c1c;
}

.rec-wbi-chip.wbi-risk-no_infection {
  background: #f0fff4;
  border: 1px solid #43a047;
  color: #1b5e20;
}

.wbi-forecast-date {
  font-size: 0.75rem;
  color: #888;
  margin-top: 0.3rem;
}

.rec-factors-details {
  margin-top: 0.5rem;
  font-size: 0.85rem;
}

.rec-factors-details summary {
  cursor: pointer;
  color: #555;
  font-weight: 600;
  user-select: none;
}

.rec-factors-list {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.3rem 1rem;
  margin: 0.6rem 0 0 0;
  padding: 0;
}

.rec-factors-list dt {
  color: #777;
  font-weight: 500;
  text-transform: capitalize;
  white-space: nowrap;
}

.rec-factors-list dd {
  margin: 0;
  color: #333;
}

</style>
