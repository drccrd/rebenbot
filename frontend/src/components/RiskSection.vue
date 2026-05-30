<template>
  <section id="sec-risk" class="risk-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      Infection Risk Assessment
    </h2>
    <div v-show="!isCollapsed" class="risk-grid">
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
</template>

<script>
import { formatOptimalConditions } from '../utils/formatters.js'

export default {
  name: 'RiskSection',
  props: {
    risks: { type: Array, required: true }
  },
  data() {
    return { isCollapsed: true }
  },
  methods: {
    formatOptimalConditions,
    expand() { this.isCollapsed = false }
  }
}
</script>
