<template>
  <section id="sec-dosage" class="dosage-calculator-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      ⚗️ Fungicide Dosage Calculator
    </h2>
    <div v-show="!isCollapsed" class="calculator-container">

      <div class="calc-tabs">
        <button :class="['calc-tab', { active: calcMethod === 'leafwall' }]" @click="calcMethod = 'leafwall'">
          🍃 Leaf Wall Area (LWA) Method
        </button>
        <button :class="['calc-tab', { active: calcMethod === 'growthstage' }]" @click="calcMethod = 'growthstage'">
          📏 Growth Stage Method
        </button>
      </div>

      <!-- Method 1: Leaf Wall Area -->
      <div v-if="calcMethod === 'leafwall'" class="calc-method">
        <h3>Leaf Wall Area (LWA) Method</h3>
        <p class="calc-description">
          Based on BBA (German Federal Biological Research Center) guidelines using canopy volume expressed as leaf wall area.
          Formula: <code>Water (L/ha) = LWA × Volume per m² × 1000</code>
        </p>
        <div class="calc-form">
          <div class="calc-input-group">
            <label>Fungicide Concentration (g/kg active):</label>
            <input type="number" v-model.number="calc.concentration" step="1" />
          </div>
          <div class="calc-input-group">
            <label>Leaf Wall Area (m²/m row):</label>
            <input type="number" v-model.number="calc.leafWallArea" step="0.1" />
            <small>Typical: 0.8–1.5 for trellis, 1.2–2.0 for bush vines. Measure canopy height × 2 sides.</small>
          </div>
          <div class="calc-input-group">
            <label>Volume per m² LWA (L/m²):</label>
            <input type="number" v-model.number="calc.volumePerM2" step="0.05" />
            <small>Typical: 0.3–0.5 L/m². Higher density canopy → higher volume.</small>
          </div>
          <div class="calc-input-group">
            <label>Fungicide base dosage (mL/ha):</label>
            <input type="number" v-model.number="calc.baseDosage" step="50" />
          </div>
          <div class="calc-input-group">
            <label>Vineyard Size (ares):</label>
            <input type="number" v-model.number="calc.vineyardSize" step="0.1" />
          </div>
        </div>
        <div class="calc-result" v-if="calc.leafWallArea && calc.volumePerM2">
          <div class="result-item">
            <span class="result-label">Water per hectare:</span>
            <span class="result-value">{{ (calc.leafWallArea * calc.volumePerM2 * 1000).toFixed(0) }} L/ha</span>
          </div>
          <div class="result-item" v-if="calc.vineyardSize">
            <span class="result-label">Total water for {{ calc.vineyardSize }} ares:</span>
            <span class="result-value">{{ (calc.leafWallArea * calc.volumePerM2 * 1000 * calc.vineyardSize / 100).toFixed(1) }} L</span>
          </div>
          <div class="result-item" v-if="calc.baseDosage">
            <span class="result-label">Fungicide per hectare (at {{ calc.baseDosage }} mL/ha):</span>
            <span class="result-value">{{ calc.baseDosage }} mL</span>
          </div>
          <div class="result-item highlight" v-if="calc.baseDosage && calc.vineyardSize">
            <span class="result-label">Fungicide for {{ calc.vineyardSize }} ares:</span>
            <span class="result-value">{{ (calc.baseDosage * calc.vineyardSize / 100).toFixed(0) }} mL</span>
          </div>
        </div>
      </div>

      <!-- Method 2: Growth Stage Method -->
      <div v-if="calcMethod === 'growthstage'" class="calc-method">
        <h3>Growth Stage Method</h3>
        <p class="calc-description">
          Adjusts spray volume based on BBCH growth stage. Early season (small canopy) uses less water;
          mid-season full canopy uses standard rates.
        </p>
        <div class="calc-form">
          <div class="calc-input-group">
            <label>BBCH Growth Stage:</label>
            <select v-model.number="calc.bbch">
              <option v-for="stage in bbchStages" :key="stage.value" :value="stage.value">
                BBCH {{ stage.range }} ({{ stage.label }})
              </option>
            </select>
          </div>
          <div class="calc-input-group">
            <label>Fungicide base dosage (mL/ha):</label>
            <input type="number" v-model.number="calc.baseDosage" step="50" />
          </div>
          <div class="calc-input-group">
            <label>Vineyard Size (ares):</label>
            <input type="number" v-model.number="calc.vineyardSize" step="0.1" />
          </div>
        </div>
        <div class="calc-result" v-if="calc.bbch">
          <div class="result-item">
            <span class="result-label">Dosage Adjustment Factor:</span>
            <span class="result-value">{{ calc.bbch >= 81 ? '0.80x (Post-veraison)' : '1.00x (Standard)' }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">Standard Spray Volume (400 L/ha):</span>
            <span class="result-value">{{ (calc.vineyardSize * 4).toFixed(2) }} L</span>
          </div>
          <div class="result-item highlight">
            <span class="result-label">Fungicide Required:</span>
            <span class="result-value">{{ (calc.baseDosage * calc.vineyardSize / 100 * (calc.bbch >= 81 ? 0.8 : 1.0)).toFixed(2) }} mL</span>
          </div>
        </div>
      </div>

    </div>
  </section>
</template>

<script>
import { BBCH_STAGES } from '../utils/constants.js'

export default {
  name: 'DosageCalculatorSection',
  data() {
    return {
      isCollapsed: false,
      calcMethod: 'leafwall',
      calc: {
        concentration: 250,
        leafWallArea: 1.2,
        volumePerM2: 0.4,
        baseDosage: 1000,
        vineyardSize: 10,
        bbch: 55
      },
      bbchStages: BBCH_STAGES
    }
  },
  methods: {
    expand() { this.isCollapsed = false }
  }
}
</script>
