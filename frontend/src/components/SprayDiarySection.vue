<template>
  <section id="sec-spray-log" class="spray-diary-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      Spray Diary
    </h2>
    <div v-show="!isCollapsed" class="diary-container">

      <!-- Log New Entry Form -->
      <div class="log-spray-card">
        <div class="form-mode-toggle">
          <button @click="entryMode = 'spray'" :class="['mode-btn', { active: entryMode === 'spray' }]">
            💊 Record Spray
          </button>
          <button @click="entryMode = 'note'" :class="['mode-btn', { active: entryMode === 'note' }]">
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
                <label for="spray-date">Application Date &amp; Time <span class="required">*</span></label>
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
                <label for="amount">Fungicide Amount Applied (liters) <span class="required">*</span></label>
                <input id="amount" type="number" v-model.number="newSpray.amountFungicideAppliedLiters" step="0.01" min="0.01" placeholder="Min 0.01 L (10ml)" required />
              </div>
            </div>
          </template>

          <!-- DIARY NOTE MODE FIELDS -->
          <template v-else>
            <div class="form-row">
              <div class="form-group">
                <label for="note-date">Date &amp; Time <span class="required">*</span></label>
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
        <h3>📋 Recent Applications (Last 10)</h3>
        <div class="sprays-list">
          <div v-for="spray in recentSprays" :key="spray.id" class="spray-item">
            <div class="spray-header">
              <span class="spray-fungicide">{{ spray.fungicide }}</span>
              <span class="spray-date">{{ formatDateTime(spray.applicationDate) }}</span>
            </div>
            <div class="spray-info">
              <span class="info-label">Disease:</span>
              <span class="info-value">{{ spray.disease }}</span>
            </div>
            <div class="spray-info" v-if="spray.amountAppliedLiters != null">
              <span class="info-label">Amount applied:</span>
              <span class="info-value">{{ spray.amountAppliedLiters }}L</span>
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
</template>

<script>
import axios from 'axios'
import { formatDateTime } from '../utils/formatters.js'

const EMPTY_FORM = {
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

export default {
  name: 'SprayDiarySection',
  props: {
    recentSprays: { type: Array, default: () => [] },
    fungicides: { type: Array, default: () => [] },
    diseases: { type: Array, default: () => [] }
  },
  emits: ['spray-recorded'],
  data() {
    return {
      isCollapsed: false,
      entryMode: 'spray',
      recordingSpray: false,
      newSpray: { ...EMPTY_FORM }
    }
  },
  methods: {
    formatDateTime,
    expand() { this.isCollapsed = false },

    prefillFromPlan(event) {
      this.entryMode = 'spray'
      const primary = event.peron || event.oidium
      this.newSpray.fungicideId = (primary && primary.productId) || ''
      this.newSpray.diseaseId = (primary && primary.diseaseId) || ''
      const now = new Date()
      this.newSpray.applicationDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
      this.isCollapsed = false
      this.$nextTick(() => {
        const el = document.getElementById('sec-spray-log')
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    },

    async recordEntry() {
      if (this.entryMode === 'spray') {
        return this.recordSpray()
      } else {
        return this.recordDiaryNote()
      }
    },

    async recordSpray() {
      if (!this.newSpray.fungicideId || !this.newSpray.diseaseId || !this.newSpray.applicationDate || !this.newSpray.amountFungicideAppliedLiters) {
        alert('Please fill in all required fields (fungicide, disease, date, amount)')
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
          amountFungicideAppliedLiters: this.newSpray.amountFungicideAppliedLiters,
          notes: this.newSpray.notes
        }
        const response = await axios.post('/api/v1/vineyard-logs/record-spray', payload)
        if (response.data && response.data.status === 'SUCCESS') {
          alert('Spray recorded successfully!')
          this.newSpray = { ...EMPTY_FORM }
          this.$emit('spray-recorded')
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
          this.newSpray = { ...EMPTY_FORM }
          this.$emit('spray-recorded')
        }
      } catch (err) {
        console.error('Error creating diary entry:', err)
        alert(`Error: ${err.response?.data?.message || err.message}`)
      } finally {
        this.recordingSpray = false
      }
    }
  }
}
</script>
