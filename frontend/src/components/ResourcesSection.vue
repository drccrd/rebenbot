<template>
  <section id="sec-resources" class="external-resources-section">
    <h2 @click="isCollapsed = !isCollapsed" class="section-header" :class="{ collapsed: isCollapsed }">
      <span class="section-toggle">{{ isCollapsed ? '▶' : '▼' }}</span>
      📚 Helpful Resources
    </h2>
    <div v-show="!isCollapsed" class="resources-grid">
      <a href="https://www.vitimeteo-bw.de/" target="_blank" rel="noopener" class="resource-link" title="Weather-based disease management tool for German viticulture">
        <span class="resource-icon">🌡️</span>
        <span class="resource-name">VitiMeteo-BW</span>
        <span class="resource-desc">Weather-based disease forecasting</span>
      </a>
      <a href="https://www.wbi.landwirtschaft-bw.de/" target="_blank" rel="noopener" class="resource-link" title="Baden-Württemberg agriculture and viticulture information">
        <span class="resource-icon">🍇</span>
        <span class="resource-name">WBI Landwirtschaft</span>
        <span class="resource-desc">Regional viticulture guidelines</span>
      </a>
      <a href="https://www.dlr.de/de/unsere-aufgaben/pflanzliche-erzeugung/rebschutz" target="_blank" rel="noopener" class="resource-link" title="State research and advisory center for viticulture">
        <span class="resource-icon">🔬</span>
        <span class="resource-name">DLR Rebschutz</span>
        <span class="resource-desc">Grapevine protection research &amp; advisory</span>
      </a>
      <a href="https://psm-api.bvl.bund.de/" target="_blank" rel="noopener" class="resource-link" title="German Federal Office for Consumer Protection — approved pesticide register">
        <span class="resource-icon">🇩🇪</span>
        <span class="resource-name">BVL PSM-Register</span>
        <span class="resource-desc">Approved pesticide products (Germany)</span>
      </a>
      <a href="https://www.frac.info/working-group/caa-working-group" target="_blank" rel="noopener" class="resource-link" title="FRAC CAA Working Group resistance guidelines">
        <span class="resource-icon">🔁</span>
        <span class="resource-name">FRAC CAA WG</span>
        <span class="resource-desc">Resistance guidelines for FRAC 40 (Peronospora)</span>
      </a>
      <a href="https://www.frac.info/working-group/sbi-working-group" target="_blank" rel="noopener" class="resource-link" title="FRAC SBI Working Group — sterol biosynthesis inhibitors for Oidium">
        <span class="resource-icon">🔁</span>
        <span class="resource-name">FRAC SBI WG</span>
        <span class="resource-desc">Resistance guidelines for DMI / FRAC 3 (Oidium)</span>
      </a>
    </div>
  </section>
</template>

<script>
export default {
  name: 'ResourcesSection',
  data() {
    return {
      isCollapsed: false
    }
  },
  methods: {
    expand() { this.isCollapsed = false }
  }
}
</script>
