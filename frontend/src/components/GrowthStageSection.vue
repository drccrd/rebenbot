<template>
  <section id="sec-growth" class="growth-stage-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      Vine Growth Stage
    </h2>
    <div v-show="!isCollapsed" class="growth-stage-card">

      <!-- Primary: VitiMeteo observed today -->
      <div v-if="latestPheno" class="gs-vitimeteo-block">
        <div class="gs-source-line">
          <span class="gs-source-badge">VitiMeteo Freiburg</span>
          <span class="gs-source-date">as of {{ formatWbiDate(latestPheno.phenoDate) }}</span>
        </div>
        <div class="gs-primary-stage">
          <span class="gs-bbch-badge">BBCH {{ latestPheno.bbchCode }}</span>
          <span class="gs-stage-label">{{ bbchLabel(latestPheno.bbchCode) }}</span>
        </div>
        <div class="gs-metrics-row">
          <span v-if="latestPheno.leafCount != null" class="gs-metric">
            🍃 {{ latestPheno.leafCount.toFixed(0) }} leaves
          </span>
          <span v-if="latestPheno.leafAreaCm2 != null" class="gs-metric"
                title="Total leaf area per shoot including growing and mature leaves">
            📐 {{ latestPheno.leafAreaCm2.toFixed(0) }} cm²
          </span>
          <span v-if="latestPheno.huglinIndex != null" class="gs-metric"
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
      <div v-if="growthStage.currentGdd != null" class="gs-gdd-row"
           :title="'Growing Degree Days accumulated from April 1 (base 10°C). Used as a local cross-check alongside VitiMeteo.'">
        📊 Local GDD estimate: {{ growthStage.currentGdd.toFixed(0) }}° → {{ growthStage.shootStageName.replace(/^BBCH \d+ — /, '') }}
      </div>

    </div>
  </section>
</template>

<script>
import { bbchLabel, formatWbiDate } from '../utils/formatters.js'

export default {
  name: 'GrowthStageSection',
  props: {
    growthStage: { type: Object, required: true },
    latestPheno: { type: Object, default: null }
  },
  data() {
    return { isCollapsed: false }
  },
  computed: {
    effectiveBbch() {
      const gddBerry = this.growthStage ? (this.growthStage.berryBbch || 0) : 0
      const phenoMax = this.latestPheno ? (this.latestPheno.maxBbchCode || 0) : 0
      return Math.max(gddBerry, phenoMax)
    }
  },
  methods: {
    bbchLabel, formatWbiDate,
    expand() { this.isCollapsed = false }
  }
}
</script>
