<template>
  <div>
    <!-- Season Buying Decision (Phase 1) -->
    <section id="sec-planner" class="season-planner-section">
      <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
        <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
        🛒 Season Buying Decision
      </h2>
      <div v-show="!isCollapsed">
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
            <div class="expiry-banner-header" @click="isExpiryBannerCollapsed = !isExpiryBannerCollapsed">
              <strong>⚠ BVL Approval Expiry Alerts — {{ expiringApprovals.length }} product{{ expiringApprovals.length !== 1 ? 's' : '' }} affected (next 120 days)</strong>
              <span class="expiry-chevron">{{ isExpiryBannerCollapsed ? '▶' : '▼' }}</span>
            </div>
            <ul v-show="!isExpiryBannerCollapsed" class="expiry-list">
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
    <section id="sec-spray-plan" class="spray-plan-section" v-if="purchasesConfirmed && sprayPlan.length > 0">
      <h2 @click="isSprayPlanCollapsed = !isSprayPlanCollapsed" class="section-header" :class="{ collapsed: isSprayPlanCollapsed }">
        <span class="section-toggle">{{ isSprayPlanCollapsed ? '▶' : '▼' }}</span>
        📅 My Spray Plan
      </h2>
      <div v-show="!isSprayPlanCollapsed">
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
  </div>
</template>

<script>
import { PERON_SLOTS, OIDIUM_SLOTS } from '../utils/constants.js'

export default {
  name: 'SeasonPlannerSection',
  props: {
    vineyard: { type: Object, default: null },
    fungicidesByDisease: { type: Object, default: () => ({}) },
    diseases: { type: Array, default: () => [] },
    expiringApprovals: { type: Array, default: () => [] }
  },
  emits: ['prefill-diary', 'update:purchasesConfirmed'],
  data() {
    return {
      isCollapsed: false,
      isSprayPlanCollapsed: false,
      isExpiryBannerCollapsed: false,
      planTargets: { peronospora: true, oidium: true },
      planSprayCount: 7,
      selectedSlots: {
        peronospora: { P1: [], P2: [], P3: [], P4: [] },
        oidium: { O1: [], O2: [], O3: [], O4: [] }
      },
      purchasesConfirmed: false
    }
  },
  computed: {
    peronDiseaseId() {
      const d = this.diseases.find(d => d.commonName && d.commonName.toLowerCase().includes('peronospora'))
      return d ? d.id : null
    },
    oidiumDiseaseId() {
      const d = this.diseases.find(d => d.commonName && d.commonName.toLowerCase().includes('oidium'))
      return d ? d.id : null
    },
    activePeronSlots() {
      return PERON_SLOTS.filter(s => !s.optional || this.planSprayCount >= 6)
    },
    activeOidiumSlots() {
      return OIDIUM_SLOTS.filter(s => !s.optional || this.planSprayCount >= 6)
    },
    peronSequence() {
      const n = this.planSprayCount
      const seq = Array(n).fill('P1')
      if (this.selectedSlots.peronospora.P2.length > 0) {
        const caaMax = Math.min(3, Math.max(1, Math.floor(n * 0.43)))
        for (let i = 1, count = 0; i < n && count < caaMax; i += 2, count++) seq[i] = 'P2'
      }
      if (this.selectedSlots.peronospora.P3.length > 0) {
        const midIdx = Math.floor(n * 0.57)
        const targetIdx = seq[midIdx] === 'P2' ? midIdx + 1 : midIdx
        if (targetIdx < n) seq[targetIdx] = 'P3'
      }
      if (this.selectedSlots.peronospora.P4.length > 0) {
        const lateIdx = n - 2
        let best = -1, bestDist = Infinity
        for (let j = 0; j < n; j++) {
          if (seq[j] === 'P1') { const d = Math.abs(j - lateIdx); if (d < bestDist) { bestDist = d; best = j } }
        }
        if (best >= 0) seq[best] = 'P4'
      }
      return seq
    },
    oidiumSequence() {
      const n = this.planSprayCount
      const seq = Array(n).fill('O1')
      if (this.selectedSlots.oidium.O2.length > 0) {
        const dmiMax = Math.min(4, Math.floor(n * 0.4))
        for (let i = 1, count = 0; i < n && count < dmiMax; i += 3, count++) seq[i] = 'O2'
      }
      if (this.selectedSlots.oidium.O3.length > 0) {
        const midIdx = Math.floor(n * 0.5)
        let best = -1, bestDist = Infinity
        for (let j = 0; j < n; j++) {
          if (seq[j] === 'O2') { const d = Math.abs(j - midIdx); if (d < bestDist) { bestDist = d; best = j } }
        }
        if (best >= 0) { seq[best] = 'O3' } else { seq[midIdx] = 'O3' }
      }
      if (this.selectedSlots.oidium.O4.length > 0) {
        const lateIdx = Math.floor(n * 0.7)
        let best = -1, bestDist = Infinity
        for (let j = 0; j < n; j++) {
          if (seq[j] === 'O1') { const d = Math.abs(j - lateIdx); if (d < bestDist) { bestDist = d; best = j } }
        }
        if (best >= 0) seq[best] = 'O4'
      }
      return seq
    },
    shoppingList() {
      const list = []
      const allProducts = Object.values(this.fungicidesByDisease).flat()
      const addSlots = (activeSlots, selectedSlots, sequence) => {
        for (const slot of activeSlots) {
          const productIds = selectedSlots[slot.id] || []
          if (productIds.length === 0) continue
          const totalUses = sequence.filter(s => s === slot.id).length
          productIds.forEach((productId, pi) => {
            const product = allProducts.find(p => p.id === productId)
            if (!product) return
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
      if (this.planTargets.peronospora) addSlots(this.activePeronSlots, this.selectedSlots.peronospora, this.peronSequence)
      if (this.planTargets.oidium) addSlots(this.activeOidiumSlots, this.selectedSlots.oidium, this.oidiumSequence)
      return list
    },
    sprayPlan() {
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
          productName: product.name, productId: product.id, diseaseId,
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
          events.push({
            idx: i,
            targetDate: targetDate.toLocaleDateString('de-DE'),
            targetDateObj: targetDate,
            peron: peronEvent, oidium: oidiumEvent,
            isPast: targetDate < today, isNext: false
          })
        }
      }
      let nextFound = false
      events.forEach(e => { if (!nextFound && !e.isPast) { e.isNext = true; nextFound = true } })
      return events
    }
  },
  mounted() {
    this.loadPersistedPlan()
  },
  methods: {
    expand() { this.isCollapsed = false },
    expandSprayPlan() { this.isSprayPlanCollapsed = false },

    peronProductsForSlot(slot) {
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

    oidiumProductsForSlot(slot) {
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

    toggleSlotProduct(disease, slotId, productId) {
      const arr = this.selectedSlots[disease][slotId]
      const idx = arr.indexOf(productId)
      if (idx >= 0) { arr.splice(idx, 1) } else { arr.push(productId) }
    },

    getProductName(productId) {
      const all = Object.values(this.fungicidesByDisease).flat()
      const p = all.find(p => p.id === productId)
      return p ? p.name : productId
    },

    productUsesInSlot(totalUses, totalProducts, productIndex) {
      if (totalProducts === 0) return 0
      return productIndex < (totalUses % totalProducts)
        ? Math.ceil(totalUses / totalProducts)
        : Math.floor(totalUses / totalProducts)
    },

    calcBuyQtyForProductInSlot(productId, productIndex, totalProducts, totalUses) {
      const uses = this.productUsesInSlot(totalUses, totalProducts, productIndex)
      const all = Object.values(this.fungicidesByDisease).flat()
      const product = all.find(p => p.id === productId)
      if (!product || !product.baseDosageMlHa || !this.vineyard) return '—'
      const qtyPerApp = Math.round(product.baseDosageMlHa * this.vineyard.sizeAres / 10000)
      const total = qtyPerApp * uses
      return total >= 1000 ? `${(total / 1000).toFixed(2)} L` : `${total} mL`
    },

    confirmPurchasesAndGeneratePlan() {
      this.purchasesConfirmed = true
      this.$emit('update:purchasesConfirmed', true)
      try {
        localStorage.setItem('rebenbot_selectedSlots', JSON.stringify(this.selectedSlots))
        localStorage.setItem('rebenbot_planConfig', JSON.stringify({
          planTargets: this.planTargets,
          planSprayCount: this.planSprayCount
        }))
      } catch (e) { /* ignore storage errors */ }
      this.isSprayPlanCollapsed = false
      this.$nextTick(() => {
        const el = document.getElementById('sec-spray-plan')
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    },

    loadPersistedPlan() {
      try {
        const slotsJson = localStorage.getItem('rebenbot_selectedSlots')
        const configJson = localStorage.getItem('rebenbot_planConfig')
        if (slotsJson) {
          const saved = JSON.parse(slotsJson)
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
          this.$emit('update:purchasesConfirmed', true)
        }
      } catch (e) { /* ignore */ }
    },

    prefillSprayDiary(event) {
      this.$emit('prefill-diary', event)
    }
  }
}
</script>
