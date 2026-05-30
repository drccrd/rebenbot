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

      <!-- Section Navigation -->
      <nav class="toc-nav" aria-label="Jump to section">
        <button v-if="sprayRecommendation" @click="scrollToSection('sprayRecommendation', 'sec-spray-rec')" :class="{ active: activeSection === 'sec-spray-rec' }" class="toc-btn">💊 Spray Rec</button>
        <button v-if="currentWeather" @click="scrollToSection('weather', 'sec-weather')" :class="{ active: activeSection === 'sec-weather' }" class="toc-btn">🌤 Weather</button>
        <button v-if="growthStage" @click="scrollToSection('growthStage', 'sec-growth')" :class="{ active: activeSection === 'sec-growth' }" class="toc-btn">🌿 Growth</button>
        <button v-if="wbiPrognosis.peronospora || wbiPrognosis.oidium" @click="scrollToSection('wbiPrognosis', 'sec-wbi')" :class="{ active: activeSection === 'sec-wbi' }" class="toc-btn">🔬 WBI</button>
        <button v-if="risks.length > 0" @click="scrollToSection('riskAssessment', 'sec-risk')" :class="{ active: activeSection === 'sec-risk' }" class="toc-btn">⚠️ Risk</button>
        <button @click="scrollToSection('sprayLog', 'sec-spray-log')" :class="{ active: activeSection === 'sec-spray-log' }" class="toc-btn">📝 Diary</button>
        <button @click="scrollToSection('seasonPlanner', 'sec-planner')" :class="{ active: activeSection === 'sec-planner' }" class="toc-btn">� Products</button>
        <button v-if="purchasesConfirmed" @click="scrollToSection('sprayPlan', 'sec-spray-plan')" :class="{ active: activeSection === 'sec-spray-plan' }" class="toc-btn">📅 Plan</button>
        <button @click="scrollToSection('dosageCalculator', 'sec-dosage')" :class="{ active: activeSection === 'sec-dosage' }" class="toc-btn">⚗️ Dosage</button>
        <button @click="scrollToSection('dataSync', 'sec-data-sync')" :class="{ active: activeSection === 'sec-data-sync' }" class="toc-btn">🔧 Sync</button>
        <button @click="scrollToSection('resources', 'sec-resources')" :class="{ active: activeSection === 'sec-resources' }" class="toc-btn">📚 Resources</button>
      </nav>

      <SprayRecommendationSection ref="sprayRecSection" v-if="sprayRecommendation" :spray-recommendation="sprayRecommendation" />
      <WeatherSection ref="weatherSection" v-if="currentWeather" :current-weather="currentWeather" :rainfall-summary="rainfallSummary" />
      <GrowthStageSection ref="growthSection" v-if="growthStage" :growth-stage="growthStage" :latest-pheno="latestPheno" />
      <WbiSection ref="wbiSection" v-if="wbiPrognosis.peronospora || wbiPrognosis.oidium" :wbi-prognosis="wbiPrognosis" :incubation-events="incubationEvents" />
      <RiskSection ref="riskSection" v-if="risks.length > 0" :risks="risks" />
      <SprayDiarySection ref="sprayDiarySection" :recent-sprays="recentSprays" :fungicides="fungicides" :diseases="diseases" @spray-recorded="fetchRecentSprays" />
      <SeasonPlannerSection ref="seasonPlannerSection" :vineyard="vineyard" :fungicides-by-disease="fungicidesByDisease" :diseases="diseases" :expiring-approvals="expiringApprovals" @prefill-diary="handlePrefillDiary" @update:purchases-confirmed="purchasesConfirmed = $event" />
      <DosageCalculatorSection ref="dosageSection" />
      <DataSyncSection ref="dataSyncSection" @bvl-sync-complete="handleBvlSyncComplete" />
      <ResourcesSection ref="resourcesSection" />
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
import { urgencyClass, formatDate } from './utils/formatters.js'
import SprayRecommendationSection from './components/SprayRecommendationSection.vue'
import WeatherSection from './components/WeatherSection.vue'
import GrowthStageSection from './components/GrowthStageSection.vue'
import WbiSection from './components/WbiSection.vue'
import RiskSection from './components/RiskSection.vue'
import SprayDiarySection from './components/SprayDiarySection.vue'
import SeasonPlannerSection from './components/SeasonPlannerSection.vue'
import DosageCalculatorSection from './components/DosageCalculatorSection.vue'
import DataSyncSection from './components/DataSyncSection.vue'
import ResourcesSection from './components/ResourcesSection.vue'

export default {
  name: 'App',
  components: {
    SprayRecommendationSection,
    WeatherSection,
    GrowthStageSection,
    WbiSection,
    RiskSection,
    SprayDiarySection,
    SeasonPlannerSection,
    DosageCalculatorSection,
    DataSyncSection,
    ResourcesSection
  },
  data() {
    return {
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
      loading: false,
      error: null,
      lastUpdate: 'Never',
      activeSection: null,
      purchasesConfirmed: false
    }
  },
  mounted() {
    this.refreshData()
    this._onScroll = () => {
      const ids = ['sec-spray-rec','sec-weather','sec-growth','sec-wbi','sec-risk','sec-spray-log','sec-planner','sec-spray-plan','sec-dosage','sec-data-sync','sec-resources']
      for (const id of ids) {
        const el = document.getElementById(id)
        if (el) {
          const rect = el.getBoundingClientRect()
          if (rect.top <= 80 && rect.bottom > 80) {
            this.activeSection = id
            return
          }
        }
      }
    }
    window.addEventListener('scroll', this._onScroll, { passive: true })
  },
  beforeUnmount() {
    if (this._onScroll) window.removeEventListener('scroll', this._onScroll)
  },
  methods: {
    // Kept for test compatibility — App.spec.js calls wrapper.vm.urgencyClass() and wrapper.vm.formatDate()
    urgencyClass,
    formatDate,

    scrollToSection(sectionKey, sectionId) {
      const refMap = {
        sprayRecommendation: 'sprayRecSection',
        weather: 'weatherSection',
        growthStage: 'growthSection',
        wbiPrognosis: 'wbiSection',
        riskAssessment: 'riskSection',
        sprayLog: 'sprayDiarySection',
        seasonPlanner: 'seasonPlannerSection',
        sprayPlan: 'seasonPlannerSection',
        dosageCalculator: 'dosageSection',
        dataSync: 'dataSyncSection',
        resources: 'resourcesSection'
      }
      const ref = this.$refs[refMap[sectionKey]]
      if (ref) {
        if (sectionKey === 'sprayPlan') {
          ref.expandSprayPlan()
        } else {
          ref.expand()
        }
      }
      this.$nextTick(() => {
        const el = document.getElementById(sectionId)
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    },

    handlePrefillDiary(event) {
      this.$refs.sprayDiarySection.prefillFromPlan(event)
    },

    async handleBvlSyncComplete() {
      await this.fetchFungicides()
      await this.fetchExpiringApprovals()
    },

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
        await this.fetchFungicidesForAllDiseases()
        await Promise.all([
          this.fetchRotationPlans(),
          this.fetchExpiringApprovals()
        ])
        await this.fetchSprayRecommendation()
        this.checkAndFireNotification()
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
        const response = await axios.get('/api/v1/weather/latest')
        if (response.data && response.data.temperatureC !== undefined) {
          this.currentWeather = response.data
        } else if (response.data && Array.isArray(response.data) && response.data.length > 0) {
          this.currentWeather = response.data[0]
        } else {
          await axios.post('/api/v1/weather/fetch?days=7')
          const retryResponse = await axios.get('/api/v1/weather/latest')
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
        await axios.post('/api/v1/risk/assess')
        const response = await axios.get('/api/v1/risk/latest')
        if (response.data && typeof response.data === 'object') {
          const diseasesResponse = await axios.get('/api/v1/diseases')
          const diseaseMap = {}
          if (diseasesResponse.data && Array.isArray(diseasesResponse.data)) {
            diseasesResponse.data.forEach(d => { diseaseMap[d.commonName] = d })
          }
          this.risks = Object.entries(response.data).map(([diseaseName, riskData]) => ({
            id: diseaseName,
            disease: diseaseMap[diseaseName] || { commonName: diseaseName, germanName: diseaseName },
            riskScore: riskData.riskScore,
            riskLevel: riskData.riskLevel,
            recommendation: riskData.recommendation,
            calculationBreakdown: riskData.calculationBreakdown,
            assessedAt: riskData.assessedAt
          }))
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
        if (perResponse?.data) this.wbiPrognosis.peronospora = perResponse.data
        if (oidResponse?.data) this.wbiPrognosis.oidium = oidResponse.data
      } catch (err) {
        console.warn('Failed to fetch WBI prognosis:', err)
      }
    },

    async fetchIncubationEvents() {
      try {
        const response = await axios.get('/api/v1/wbi/incubation/active')
        if (Array.isArray(response.data)) this.incubationEvents = response.data
      } catch (err) {
        console.warn('Failed to fetch incubation events:', err)
      }
    },

    async fetchLatestPheno() {
      try {
        const response = await axios.get('/api/v1/wbi/pheno/latest')
        if (response.data) this.latestPheno = response.data
      } catch (err) {
        console.warn('Failed to fetch latest pheno:', err)
      }
    },

    async fetchRainfallSummary() {
      try {
        const response = await axios.get('/api/v1/spray/rainfall-summary')
        if (response.data) this.rainfallSummary = response.data
      } catch (err) {
        console.warn('Failed to fetch rainfall summary:', err)
      }
    },

    async fetchGrowthStage() {
      try {
        const response = await axios.get('/api/v1/growth-stage/current')
        if (response.data) this.growthStage = response.data
      } catch (err) {
        console.warn('Failed to fetch growth stage:', err)
      }
    },

    async fetchFungicides() {
      try {
        const response = await axios.get('/api/v1/fungicides/all')
        const data = response.data?.fungicides ?? response.data
        if (Array.isArray(data)) {
          this.fungicides = data.slice().sort((a, b) => a.name.localeCompare(b.name, 'de'))
        }
      } catch (err) {
        console.warn('Failed to fetch fungicides:', err)
      }
    },

    async fetchDiseases() {
      try {
        const response = await axios.get('/api/v1/diseases')
        if (Array.isArray(response.data)) this.diseases = response.data
      } catch (err) {
        console.warn('Failed to fetch diseases:', err)
      }
    },

    async fetchFungicidesForAllDiseases() {
      this.loadingFungicides = true
      try {
        for (const disease of this.diseases) {
          try {
            const response = await axios.get(`/api/v1/fungicide-management/by-disease/${disease.id}`)
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

    async fetchRecentSprays() {
      try {
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
        if (response.data) this.sprayRecommendation = response.data
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
    }
  }
}
</script>

<style>
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

/* Section Navigation (ToC) */
.toc-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.97);
  backdrop-filter: blur(6px);
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 16px;
  overflow-x: auto;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.10);
  scrollbar-width: none;
}

.toc-nav::-webkit-scrollbar { display: none; }

.toc-btn {
  flex-shrink: 0;
  background: #f0f0f5;
  border: 1px solid #d8d8e8;
  border-radius: 20px;
  padding: 5px 12px;
  font-size: 0.8rem;
  cursor: pointer;
  color: #444;
  white-space: nowrap;
  transition: background 0.2s, color 0.2s, border-color 0.2s;
}

.toc-btn:hover {
  background: #e0e0f0;
  border-color: #667eea;
  color: #667eea;
}

.toc-btn.active {
  background: #667eea;
  border-color: #667eea;
  color: white;
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
