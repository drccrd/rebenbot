<template>
  <section id="sec-data-sync" class="data-sync-section">
    <h2 @click="toggle" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      � Data Sync (Admin)
    </h2>
    <div v-show="!isCollapsed" class="data-sync-container">

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
        <button @click="triggerBvlSync" :disabled="syncingBvl" class="btn-sync">
          {{ syncingBvl ? 'Syncing…' : 'Sync Now' }}
        </button>
        <p v-if="bvlSyncMessage" class="sync-feedback" :class="{ 'sync-feedback-error': bvlSyncMessage.startsWith('Error') }">{{ bvlSyncMessage }}</p>
      </div>

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
</template>

<script>
import axios from 'axios'

export default {
  name: 'DataSyncSection',
  emits: ['bvl-sync-complete'],
  data() {
    return {
      isCollapsed: true,
      syncStatus: null,
      syncingBvl: false,
      bvlSyncMessage: null
    }
  },
  methods: {
    toggle() {
      const wasCollapsed = this.isCollapsed
      this.isCollapsed = !this.isCollapsed
      if (wasCollapsed) this.fetchSyncStatus()
    },

    expand() {
      const wasCollapsed = this.isCollapsed
      this.isCollapsed = false
      if (wasCollapsed) this.fetchSyncStatus()
    },

    async fetchSyncStatus() {
      try {
        const response = await axios.get('/api/v1/admin/sync/status')
        this.syncStatus = response.data
      } catch (err) {
        console.error('Error fetching sync status:', err)
      }
    },

    async triggerBvlSync() {
      this.syncingBvl = true
      this.bvlSyncMessage = null
      try {
        const response = await axios.post('/api/v1/admin/sync/bvl-api')
        this.bvlSyncMessage = response.data?.message || 'Sync completed'
        await this.fetchSyncStatus()
        this.$emit('bvl-sync-complete')
      } catch (err) {
        this.bvlSyncMessage = `Error: ${err.response?.data?.message || err.message}`
      } finally {
        this.syncingBvl = false
      }
    }
  }
}
</script>
