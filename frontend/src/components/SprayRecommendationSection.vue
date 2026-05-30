<template>
  <section id="sec-spray-rec" class="spray-rec-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      💊 Next Spray Recommendation
      <span class="rec-urgency-badge" :class="urgencyClass(sprayRecommendation.urgency)">
        {{ sprayRecommendation.urgency.replace('_', ' ') }}
      </span>
    </h2>
    <div v-show="!isCollapsed" class="spray-rec-card" :class="urgencyClass(sprayRecommendation.urgency)">
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
</template>

<script>
import { formatDate, formatWbiDate, urgencyClass } from '../utils/formatters.js'

export default {
  name: 'SprayRecommendationSection',
  props: {
    sprayRecommendation: { type: Object, required: true }
  },
  data() {
    return { isCollapsed: false }
  },
  methods: {
    formatDate, formatWbiDate, urgencyClass,
    expand() { this.isCollapsed = false }
  }
}
</script>
