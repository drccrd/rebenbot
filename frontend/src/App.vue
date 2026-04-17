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

      <!-- Current Weather -->
      <section class="weather-section" v-if="currentWeather">
        <h2>Current Weather</h2>
        <div class="weather-grid">
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
        </div>
      </section>

      <!-- Growth Stage -->
      <section class="growth-stage-section" v-if="growthStage">
        <h2>Vine Growth Stage</h2>
        <div class="growth-stage-card">
          <div class="stage-display">
            <div class="stage-icon">🌱</div>
            <div class="stage-info">
              <p class="stage-code">{{ growthStage.stageBbchName }}</p>
              <p class="stage-gdd" :title="'Growing Degree Days: cumulative heat units since April 1st (base temp 10°C)'">
                Accumulated GDD: {{ growthStage.currentGdd.toFixed(1) }}°
              </p>
              <p class="stage-source" v-if="growthStage.isManualOverride" style="color: #ff9800;">
                ✏️ Manually set
              </p>
              <p class="stage-source" v-else style="color: #4caf50;">
                📊 Calculated from weather data
              </p>
            </div>
          </div>
          
          <div class="stage-controls">
            <div class="control-group">
              <label for="stage-select">Override with manual stage:</label>
              <select id="stage-select" v-model="selectedStageOverride" class="stage-select">
                <option value="">Use automatic calculation</option>
                <option v-for="(description, code) in availableStages" :key="code" :value="code">
                  {{ description }}
                </option>
              </select>
            </div>
            <button 
              @click="setManualGrowthStage" 
              :disabled="!selectedStageOverride || settingStage"
              class="btn-set-stage"
            >
              {{ settingStage ? 'Setting...' : 'Set Manual Stage' }}
            </button>
            <button 
              @click="useAutomaticGrowthStage"
              :disabled="!growthStage.isManualOverride || settingStage"
              class="btn-automatic"
            >
              {{ settingStage ? 'Updating...' : 'Use Automatic' }}
            </button>
          </div>
        </div>
      </section>

      <!-- Disease Risk Assessment -->
      <section class="risk-section" v-if="risks.length > 0">
        <h2>Infection Risk Assessment</h2>
        <div class="risk-grid">
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

      <!-- Rainfall & Spray Timing -->
      <section class="spray-timing-section" v-if="rainfallSummary || sprayWindow">
        <h2>Rainfall & Spray Timing</h2>
        <div class="spray-grid">
          <!-- Rainfall Information -->
          <div class="rainfall-card" v-if="rainfallSummary">
            <h3>Rainfall Summary (24h)</h3>
            <div class="rainfall-details">
              <div class="rainfall-metric">
                <span class="metric-label">Total Rainfall</span>
                <span class="metric-value">{{ rainfallSummary.rainfall24hMm.toFixed(1) }} mm</span>
              </div>
              <div class="rainfall-metric" v-if="rainfallSummary.hoursSinceSignificantRain">
                <span class="metric-label">Last Significant Rain (>2mm)</span>
                <span class="metric-value">{{ rainfallSummary.hoursSinceSignificantRain.toFixed(1) }} hours ago</span>
              </div>
              <div class="rainfall-metric" v-else>
                <span class="metric-label">Last Significant Rain</span>
                <span class="metric-value">None in 72h</span>
              </div>
              <p class="rainfall-recommendation">{{ rainfallSummary.recommendation }}</p>
            </div>
          </div>

          <!-- Spray Timing Window -->
          <div class="spray-window-card" v-if="sprayWindow">
            <h3>Peronospora Spray Window</h3>
            <div class="spray-details">
              <div class="spray-strategy" :class="'strategy-' + sprayWindow.strategy.toLowerCase()" :title="sprayWindow.strategyReasoning">
                <span class="strategy-label">Strategy:</span>
                <span class="strategy-value">{{ sprayWindow.strategy }}</span>
              </div>
              <div class="spray-metric" :title="sprayWindow.preferredTimeReasoning">
                <span class="metric-label">Preferred Time</span>
                <span class="metric-value">{{ formatDateTime(sprayWindow.preferredTime) }}</span>
              </div>
              <div class="spray-metric" :title="sprayWindow.windowReasoning">
                <span class="metric-label">Window</span>
                <span class="metric-value">{{ formatDateTime(sprayWindow.windowStart) }} to {{ formatDateTime(sprayWindow.windowEnd) }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Spray Diary -->
      <section class="spray-diary-section">
        <h2>Spray Diary</h2>
        <div class="diary-container">
          <!-- Log New Spray Form -->
          <div class="log-spray-card">
            <h3>📝 Log Spray Application</h3>
            <form @submit.prevent="recordSpray" class="spray-form">
              <div class="form-row">
                <div class="form-group">
                  <label for="fungicide-select">Fungicide</label>
                  <select id="fungicide-select" v-model="newSpray.fungicideId" required>
                    <option value="">Select a fungicide...</option>
                    <option v-for="fung in fungicides" :key="fung.id" :value="fung.id">
                      {{ fung.name }}
                    </option>
                  </select>
                </div>
                <div class="form-group">
                  <label for="disease-select">Disease Target</label>
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
                  <label for="spray-date">Application Date & Time</label>
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
                <div class="form-group full-width">
                  <label for="notes">Notes</label>
                  <textarea id="notes" v-model="newSpray.notes" placeholder="Conditions, application notes, etc."></textarea>
                </div>
              </div>

              <button type="submit" :disabled="recordingSpray" class="btn-submit">
                {{ recordingSpray ? 'Recording...' : 'Record Spray' }}
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

      <!-- Fungicide Recommendations -->
      <section class="recommendations-section" v-if="recommendations.length > 0">
        <h2>Recommended Fungicides</h2>
        <div class="recommendations-list">
          <div 
            v-for="(group, disease) in groupRecommendations()" 
            :key="disease"
            class="recommendation-group"
          >
            <h3>{{ disease }}</h3>
            <div class="fungicide-cards">
              <div 
                v-for="(rec, index) in group.slice(0, 3)" 
                :key="index"
                class="fungicide-card"
                :class="{ 'not-applicable': !rec.applicable }"
              >
                <div class="fungicide-header">
                  <h4>{{ rec.name }}</h4>
                  <span class="score-badge">Score: {{ (rec.score * 100).toFixed(0) }}%</span>
                </div>
                <p class="active-substance">{{ rec.activeSubstance }}</p>
                <p class="timing">⏱️ {{ rec.timing }}</p>
                <p class="rationale">{{ rec.rationale }}</p>
                <div class="fungicide-details">
                  <span v-if="rec.applicable" class="phi-ok">✓ PHI OK ({{ rec.minDaysBeforeHarvest }} days)</span>
                  <span v-else class="phi-warn">⚠ PHI Issue: Need {{ rec.minDaysBeforeHarvest }} days</span>
                </div>
                <div class="dosage-info" v-if="rec.dosageMlPer100L && vineyard">
                  <span class="dosage-label">Dosage for {{ vineyard.sizeAres }} are:</span>
                  <span class="dosage-value">{{ calculateFungicideDosage(rec.dosageMlPer100L) }} L</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Resistance Prevention Guidelines -->
      <section class="resistance-guidelines-section" v-if="recommendations.length > 0">
        <h2>🔒 Fungicide Resistance Prevention</h2>
        <div class="guidelines-container">
          <div class="guideline-box">
            <h3>Resistance Management Strategy</h3>
            <ul class="guidelines-list">
              <li><strong>Rotate active substances:</strong> Use different chemical classes in successive applications to prevent resistance buildup in fungal populations</li>
              <li><strong>Limit repeats:</strong> Avoid applying the same active substance more than 2-3 times per season. Alternate with other modes of action</li>
              <li><strong>Monitor efficacy:</strong> Track disease control effectiveness. Reduced efficacy may indicate emerging resistance</li>
              <li><strong>Combination products:</strong> Use multi-active fungicides when available to reduce selection pressure on individual substances</li>
              <li><strong>Preventive approach:</strong> Maintain preventive spray schedules rather than waiting for infections (helps prevent resistant population development)</li>
              <li><strong>Regional compliance:</strong> Follow Baden-Württemberg and German fungicide guidelines for resistance management</li>
            </ul>
          </div>
          <div class="guideline-box disease-specific">
            <h3>Disease-Specific Resistance Info</h3>
            <div class="disease-resistance">
              <strong>Peronospora:</strong> Resistance to phosphonites and strobilurins is documented. Prioritize multi-active formulations.
            </div>
            <div class="disease-resistance">
              <strong>Oidium:</strong> Resistance to sulfur and DMIs may develop. Rotate with quinolines and other modes of action.
            </div>
            <div class="disease-resistance">
              <strong>Botrytis:</strong> Resistance to benzimidazoles is common. Alternate with fluazinam or multi-active products.
            </div>
          </div>
        </div>
      </section>

      <!-- Fungicide Buying Guide -->
      <section class="buying-guide-section">
        <h2>💰 Fungicide Buying Guide for the Year</h2>
        <div v-if="loadingFungicides" class="loading-message">
          Loading fungicide recommendations...
        </div>
        <div v-else-if="Object.keys(fungicidesByDisease).length === 0" class="no-data-message">
          No fungicide data available. Please refresh the page.
        </div>
        <div v-else class="buying-guide-container">
          <div v-for="disease in diseases" :key="disease.id" class="disease-buying-guide">
            <h3>{{ disease.icon || '🍇' }} For {{ disease.commonName }}</h3>
            <div v-if="fungicidesByDisease[disease.id] && fungicidesByDisease[disease.id].length > 0">
              <div 
                v-for="fungicide in fungicidesByDisease[disease.id]" 
                :key="fungicide.id" 
                class="buying-recommendation"
              >
                <div class="product-name">{{ fungicide.name }} ({{ fungicide.activeSubstance }})</div>
                <div class="product-details">
                  <span class="detail-item"><strong>Concentration:</strong> {{ fungicide.concentration }}%</span>
                  <span v-if="fungicide.manufacturer" class="detail-item"><strong>Manufacturer:</strong> {{ fungicide.manufacturer }}</span>
                  <span v-if="fungicide.fracCode" class="detail-item"><strong>FRAC Code:</strong> {{ fungicide.fracCode }}</span>
                  <span v-if="fungicide.fracDescription" class="detail-item"><strong>Mode of Action:</strong> {{ fungicide.fracDescription }}</span>
                  <span class="detail-item"><strong>Recommendation:</strong> Rotate with different FRAC codes to prevent resistance</span>
                </div>
              </div>
            </div>
            <div v-else class="no-products-message">
              No approved fungicides available for {{ disease.commonName }}
            </div>
          </div>
        </div>
      </section>

      <!-- Seasonal Spray Schedule -->
      <section class="spray-schedule-section">
        <h2>📅 Seasonal Spray Schedule (Year-Round Plan)</h2>
        <div class="schedule-note">Adjust dates based on actual weather conditions and disease pressure. Monitor forecasts weekly.</div>
        <div class="schedule-grid">
          <div class="month-schedule">
            <h3>April - Bud Break (Risk: MEDIUM)</h3>
            <div class="schedule-entry">
              <strong>Week 1-2:</strong> Start preventive sulfur (Netzschwefel 1.5 kg/ha) for Oidium risk
            </div>
            <div class="schedule-entry">
              <strong>Week 3-4:</strong> First Peronospora spray - Dithane (2-3 g/100L) if rain + high humidity
            </div>
          </div>

          <div class="month-schedule">
            <h3>May - High Risk Oidium Window (Risk: HIGH for Oidium)</h3>
            <div class="schedule-entry">
              <strong>Week 1:</strong> Sulfur spray #2 (every 7-10 days during high risk)
            </div>
            <div class="schedule-entry">
              <strong>Week 2:</strong> Flint rotation spray (1 L/ha) - prevents sulfur resistance
            </div>
            <div class="schedule-entry">
              <strong>Week 3:</strong> Back to sulfur #3
            </div>
            <div class="schedule-entry">
              <strong>Week 4:</strong> Peronospora check - Dithane if conditions warrant
            </div>
          </div>

          <div class="month-schedule">
            <h3>June - Declining Oidium, Rising Peronospora (Risk: MEDIUM-HIGH)</h3>
            <div class="schedule-entry">
              <strong>Week 1-2:</strong> Sulfur #4 (frequency decreasing as temperatures rise)
            </div>
            <div class="schedule-entry">
              <strong>Week 3:</strong> Switch to Peronospora focus - Cuproxat (2-4 L/ha) for rotation
            </div>
            <div class="schedule-entry">
              <strong>Week 4:</strong> Peronospora spray #2 as needed based on rain forecast
            </div>
          </div>

          <div class="month-schedule">
            <h3>July - Mid-Season (Risk: VARIABLE)</h3>
            <div class="schedule-entry">
              <strong>Week 1-2:</strong> Peronospora maintenance - Delan (1.5 g/100L) for rotation
            </div>
            <div class="schedule-entry">
              <strong>Week 3:</strong> Growth stage: BBCH 70-75 - Dosage adjustment begins
            </div>
            <div class="schedule-entry">
              <strong>Week 4:</strong> Monitor rainfall patterns, spray if rain + cold expected
            </div>
          </div>

          <div class="month-schedule">
            <h3>August - Pre-Veraison (Risk: DECLINING but monitor)</h3>
            <div class="schedule-entry">
              <strong>Week 1-2:</strong> Peronospora spray #3 - Dithane (rotate back, 2 g/100L)
            </div>
            <div class="schedule-entry">
              <strong>Week 3:</strong> Growth stage: BBCH 80 (Veraison) - Apply 20% dosage reduction
            </div>
            <div class="schedule-entry">
              <strong>Week 4:</strong> Final inspection for disease pressure
            </div>
          </div>

          <div class="month-schedule">
            <h3>September - Harvest Period (Risk: NONE)</h3>
            <div class="schedule-entry">
              <strong>Week 1-2:</strong> Harvest in progress - NO SPRAYING after August
            </div>
            <div class="schedule-entry">
              <strong>Week 3-4:</strong> Post-harvest cleanup and disease monitoring
            </div>
          </div>

          <div class="month-schedule">
            <h3>October - Post-Harvest (Risk: NONE)</h3>
            <div class="schedule-entry">
              <strong>All week:</strong> Season complete - monitoring only
            </div>
            <div class="schedule-entry">
              <strong>Post-harvest:</strong> Review spray effectiveness, plan next season
            </div>
          </div>
        </div>
      </section>

      <!-- Dosage Calculator -->
      <section class="dosage-calculator-section">
        <h2>⚗️ Fungicide Dosage Calculator</h2>
        <div class="calculator-container">
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
                <label>Fungicide Base Dosage (ml/100L):</label>
                <input type="number" v-model.number="calc.baseDosage" step="0.1" />
              </div>
              <div class="calc-input-group">
                <label>Vineyard Size (ares):</label>
                <input type="number" v-model.number="calc.vineyardSize" step="0.1" />
              </div>
              <div class="calc-input-group">
                <label>Growth Stage (BBCH):</label>
                <select v-model.number="calc.bbch">
                  <option value="0">BBCH 00-09 (Pre-budburst)</option>
                  <option value="10">BBCH 10-19 (Budburst)</option>
                  <option value="25">BBCH 25-29 (5-9 leaves)</option>
                  <option value="35">BBCH 35-39 (Visible flower clusters)</option>
                  <option value="45">BBCH 45-49 (Bloom)</option>
                  <option value="55">BBCH 55-59 (Fruitset)</option>
                  <option value="65">BBCH 65-69 (Berries pea-sized)</option>
                  <option value="75">BBCH 75-79 (Véraison beginning)</option>
                  <option value="81">BBCH 80-81 (Post-véraison - 20% reduction)</option>
                  <option value="89">BBCH 89 (Harvest)</option>
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
                <span class="result-value">{{ (calc.baseDosage * (calc.vineyardSize * 4) / 100 * (calc.bbch >= 81 ? 0.8 : 1.0)).toFixed(2) }} ml</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Fungicide Buying Guide -->

      <!-- External Resources -->
      <section class="external-resources-section">
        <h2>📚 Helpful Resources</h2>
        <div class="resources-grid">
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

export default {
  name: 'App',
  data() {
    return {
      vineyard: null,
      currentWeather: null,
      risks: [],
      recommendations: [],
      rainfallSummary: null,
      sprayWindow: null,
      growthStage: null,
      availableStages: {},
      selectedStageOverride: '',
      settingStage: false,
      recentSprays: [],
      fungicides: [],
      diseases: [],
      fungicidesByDisease: {},
      loadingFungicides: false,
      calcMethod: 'leafwall',
      calc: {
        concentration: 250,
        leafWallArea: 12,
        volumePerM2: 0.4,
        baseDosage: 250,
        vineyardSize: 10,
        bbch: 55
      },
      currentYear: new Date().getFullYear(),
      newSpray: {
        fungicideId: '',
        diseaseId: '',
        applicationDate: '',
        growthStageBbch: '',
        temperatureC: null,
        humidityPercent: null,
        windSpeedMsec: null,
        notes: ''
      },
      recordingSpray: false,
      loading: false,
      error: null,
      lastUpdate: 'Never'
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
          this.fetchRecommendations(),
          this.fetchRainfallSummary(),
          this.fetchSprayWindow(),
          this.fetchGrowthStage(),
          this.fetchAvailableStages(),
          this.fetchFungicides(),
          this.fetchDiseases(),
          this.fetchRecentSprays()
        ])
        // After diseases are loaded, fetch fungicides for each disease
        await this.fetchFungicidesForAllDiseases()
        this.lastUpdate = new Date().toLocaleTimeString()
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
    async fetchRecommendations() {
      try {
        const response = await axios.get('/api/v1/fungicides/latest-recommendations')
        console.log('Recommendations response:', response.data)
        if (response.data && response.data.recommendations) {
          // Flatten recommendations for easier display
          this.recommendations = []
          for (const [disease, recs] of Object.entries(response.data.recommendations)) {
            recs.forEach(rec => {
              this.recommendations.push({ ...rec, disease })
            })
          }
        } else if (response.data && typeof response.data === 'object') {
          // If response is already a flat object of disease->recommendations
          this.recommendations = []
          for (const [disease, recs] of Object.entries(response.data)) {
            if (Array.isArray(recs)) {
              recs.forEach(rec => {
                this.recommendations.push({ ...rec, disease })
              })
            }
          }
        }
      } catch (err) {
        console.warn('Failed to fetch recommendations:', err)
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
    async fetchSprayWindow() {
      try {
        // Get current weather for temp parameter
        let tempC = this.currentWeather?.temperatureC || 15.0
        const response = await axios.get(`/api/v1/spray/window/peronospora?currentTemperatureC=${tempC}`)
        console.log('Spray window response:', response.data)
        if (response.data) {
          this.sprayWindow = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch spray window:', err)
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
    async fetchAvailableStages() {
      try {
        const response = await axios.get('/api/v1/growth-stage/available-stages')
        if (response.data) {
          this.availableStages = response.data
        }
      } catch (err) {
        console.warn('Failed to fetch available stages:', err)
      }
    },
    async setManualGrowthStage() {
      if (!this.selectedStageOverride) return
      this.settingStage = true
      try {
        const response = await axios.post(
          `/api/v1/growth-stage/set-manual?stageName=${this.selectedStageOverride}`
        )
        if (response.data) {
          this.growthStage = response.data
          this.selectedStageOverride = ''
        }
      } catch (err) {
        console.error('Failed to set manual growth stage:', err)
        this.error = `Failed to set growth stage: ${err.message}`
      } finally {
        this.settingStage = false
      }
    },
    async useAutomaticGrowthStage() {
      this.settingStage = true
      try {
        const response = await axios.post('/api/v1/growth-stage/use-automatic')
        if (response.data) {
          this.growthStage = response.data
          this.selectedStageOverride = ''
        }
      } catch (err) {
        console.error('Failed to switch to automatic growth stage:', err)
        this.error = `Failed to update growth stage: ${err.message}`
      } finally {
        this.settingStage = false
      }
    },
    groupRecommendations() {
      const grouped = {}
      this.recommendations.forEach(rec => {
        if (!grouped[rec.disease]) {
          grouped[rec.disease] = []
        }
        grouped[rec.disease].push(rec)
      })
      return grouped
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
    formatDateTime(isoString) {
      if (!isoString) return 'N/A'
      try {
        // Handle both ISO string and array formats
        let date
        if (Array.isArray(isoString)) {
          // If it's an array [year, month, day, hour, minute, second, nanos]
          const [year, month, day, hour, minute, second] = isoString
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
    async fetchRecentSprays() {
      try {
        const response = await axios.get('/api/v1/spray-diary/recent/1')
        if (response.data && response.data.sprays) {
          this.recentSprays = response.data.sprays
        }
      } catch (err) {
        console.warn('Failed to fetch recent sprays:', err)
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
          notes: this.newSpray.notes
        }

        const response = await axios.post('/api/v1/spray-diary/record', payload)
        
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
            notes: ''
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
    calculateFungicideDosage(dosageMlPer100L) {
      if (!dosageMlPer100L || !this.vineyard) {
        return 'N/A'
      }
      
      // Standard spray volume: 400 L/hectare (4 L per are)
      const sprayVolumePerAre = 4
      const totalWaterLiters = this.vineyard.sizeAres * sprayVolumePerAre
      
      // Calculate dosage: (ml per 100L) * (totalWater L / 100 L)
      const dosageLiters = (dosageMlPer100L / 100.0) * (totalWaterLiters / 100.0)
      
      return dosageLiters.toFixed(2)
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

.recommendations-list {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.recommendation-group h3 {
  color: #667eea;
  margin-bottom: 1rem;
  font-size: 1.1rem;
}

.fungicide-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.fungicide-card {
  background: #f9f9f9;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.5rem;
  transition: all 0.3s ease;
}

.fungicide-card:hover {
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
  transform: translateY(-2px);
}

.fungicide-card.not-applicable {
  opacity: 0.7;
  background: #fafafa;
}

.fungicide-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.8rem;
}

.fungicide-header h4 {
  margin: 0;
  color: #333;
  font-size: 1rem;
  flex: 1;
}

.score-badge {
  background: #667eea;
  color: white;
  padding: 0.3rem 0.7rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  margin-left: 0.5rem;
  white-space: nowrap;
}

.active-substance {
  color: #999;
  font-size: 0.85rem;
  margin: 0.3rem 0 0.8rem 0;
}

.timing {
  background: #e3f2fd;
  color: #1976d2;
  padding: 0.5rem;
  border-radius: 4px;
  font-size: 0.9rem;
  margin-bottom: 0.8rem;
}

.rationale {
  color: #666;
  font-size: 0.85rem;
  line-height: 1.4;
  margin: 0.8rem 0;
}

.fungicide-details {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #eee;
}

.phi-ok {
  color: #4caf50;
  font-size: 0.85rem;
  font-weight: 600;
}

.phi-warn {
  color: #ff9800;
  font-size: 0.85rem;
  font-weight: 600;
}

.dosage-info {
  background: #f0f4ff;
  border-left: 3px solid #3f51b5;
  padding: 8px 12px;
  margin-top: 8px;
  border-radius: 2px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
}

.dosage-label {
  color: #555;
  font-weight: 600;
}

.dosage-value {
  color: #3f51b5;
  font-weight: 700;
  font-size: 1rem;
}

.spray-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
}

.rainfall-card {
  background: linear-gradient(135deg, #64b5f6 0%, #42a5f5 100%);
  color: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 4px 12px rgba(66, 165, 245, 0.2);
}

.rainfall-card h3 {
  margin: 0 0 1rem 0;
  font-size: 1.1rem;
}

.rainfall-details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.rainfall-metric {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-label {
  font-size: 0.9rem;
  opacity: 0.9;
}

.metric-value {
  font-size: 1.1rem;
  font-weight: 600;
}

.rainfall-recommendation {
  margin-top: 0.5rem;
  font-size: 0.85rem;
  opacity: 0.95;
  font-style: italic;
}

.spray-window-card {
  background: linear-gradient(135deg, #81c784 0%, #66bb6a 100%);
  color: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 4px 12px rgba(102, 187, 106, 0.2);
}

.spray-window-card h3 {
  margin: 0 0 1rem 0;
  font-size: 1.1rem;
}

.spray-details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.spray-strategy {
  background: rgba(255, 255, 255, 0.2);
  padding: 0.8rem;
  border-radius: 6px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.strategy-label {
  font-size: 0.9rem;
  opacity: 0.9;
}

.strategy-value {
  font-size: 1rem;
  font-weight: 600;
  padding: 0.3rem 0.8rem;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 4px;
}

.spray-metric {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.spray-metric:last-child {
  border-bottom: none;
}

/* Growth Stage Styles */
.growth-stage-section {
  background: linear-gradient(135deg, #4caf50 0%, #45a049 100%);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  color: white;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.growth-stage-section h2 {
  margin: 0 0 1.5rem 0;
  font-size: 1.3rem;
}

.growth-stage-card {
  display: flex;
  gap: 2rem;
  align-items: flex-start;
}

.stage-display {
  display: flex;
  gap: 1.5rem;
  flex: 1;
  background: rgba(255, 255, 255, 0.15);
  padding: 1.5rem;
  border-radius: 8px;
}

.stage-icon {
  font-size: 3rem;
  line-height: 1;
}

.stage-info {
  flex: 1;
}

.stage-code {
  margin: 0 0 0.5rem 0;
  font-size: 1.1rem;
  font-weight: 600;
}

.stage-gdd {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
  opacity: 0.95;
  cursor: help;
  border-bottom: 1px dotted rgba(255, 255, 255, 0.3);
}

.stage-source {
  margin: 0;
  font-size: 0.9rem;
  opacity: 0.9;
}

.stage-controls {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  background: rgba(255, 255, 255, 0.15);
  padding: 1.5rem;
  border-radius: 8px;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.control-group label {
  font-size: 0.9rem;
  font-weight: 500;
}

.stage-select {
  padding: 0.7rem;
  border: none;
  border-radius: 4px;
  background: white;
  color: #333;
  font-size: 0.95rem;
  cursor: pointer;
}

.btn-set-stage,
.btn-automatic {
  padding: 0.7rem 1rem;
  border: none;
  border-radius: 4px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.95rem;
}

.btn-set-stage {
  background: #ff9800;
  color: white;
}

.btn-set-stage:hover:not(:disabled) {
  background: #f57c00;
  transform: translateY(-2px);
}

.btn-automatic {
  background: rgba(255, 255, 255, 0.9);
  color: #4caf50;
}

.btn-automatic:hover:not(:disabled) {
  background: white;
  transform: translateY(-2px);
}

.btn-set-stage:disabled,
.btn-automatic:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
.risk-badge[title],
.spray-strategy[title],
.spray-metric[title] {
  transition: opacity 0.2s ease;
}

.risk-score[title]:hover,
.risk-optimal[title]:hover,
.risk-badge[title]:hover,
.spray-strategy[title]:hover,
.spray-metric[title]:hover {
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

.product-name {
  font-weight: 600;
  color: #1b5e20;
  margin-bottom: 8px;
}

.product-details {
  display: grid;
  gap: 6px;
}

.detail-item {
  font-size: 13px;
  color: #333;
  line-height: 1.4;
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
</style>
