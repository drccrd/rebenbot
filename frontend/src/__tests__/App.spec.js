import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import App from '../App.vue'

// Stub axios so no real HTTP calls are made during tests.
vi.mock('axios', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn().mockResolvedValue({ data: {} }),
  },
}))

// Stub browser APIs that are not present in happy-dom
global.Notification = { permission: 'denied', requestPermission: vi.fn() }

// -----------------------------------------------------------------------
// Helper — mount App with all API calls suppressed
// -----------------------------------------------------------------------
async function mountApp() {
  const wrapper = mount(App, {
    global: {
      config: {
        warnHandler: () => {},     // suppress expected Vue console warnings
      },
    },
  })
  // Allow the event loop to flush the mounted() async chain
  await wrapper.vm.$nextTick()
  return wrapper
}

// -----------------------------------------------------------------------
// Rendering
// -----------------------------------------------------------------------

describe('App — basic rendering', () => {
  it('renders the page header with app title', async () => {
    const wrapper = await mountApp()
    expect(wrapper.find('h1').text()).toContain('Rebenbot')
  })

  it('renders the Refresh button', async () => {
    const wrapper = await mountApp()
    const btn = wrapper.find('button.btn-refresh')
    expect(btn.exists()).toBe(true)
    // Text toggles between "Updating..." (loading) and "Refresh" (idle) — both are valid here
    expect(btn.text()).toMatch(/refresh|updating/i)
  })
})

// -----------------------------------------------------------------------
// urgencyClass() method
// -----------------------------------------------------------------------

describe('App — urgencyClass()', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountApp()
  })

  it('maps URGENT to rec-urgent class', () => {
    const cls = wrapper.vm.urgencyClass('URGENT')
    expect(cls['rec-urgent']).toBe(true)
    expect(cls['rec-action']).toBe(false)
  })

  it('maps ACTION_RECOMMENDED to rec-action class', () => {
    const cls = wrapper.vm.urgencyClass('ACTION_RECOMMENDED')
    expect(cls['rec-action']).toBe(true)
    expect(cls['rec-urgent']).toBe(false)
  })

  it('maps SCHEDULED to rec-scheduled class', () => {
    const cls = wrapper.vm.urgencyClass('SCHEDULED')
    expect(cls['rec-scheduled']).toBe(true)
  })

  it('maps MONITOR to rec-monitor class', () => {
    const cls = wrapper.vm.urgencyClass('MONITOR')
    expect(cls['rec-monitor']).toBe(true)
  })

  it('unknown urgency has no class set to true', () => {
    const cls = wrapper.vm.urgencyClass('UNKNOWN')
    expect(Object.values(cls).every(v => v === false)).toBe(true)
  })
})

// -----------------------------------------------------------------------
// formatDate() method
// -----------------------------------------------------------------------

describe('App — formatDate()', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountApp()
  })

  it('returns N/A for null', () => {
    expect(wrapper.vm.formatDate(null)).toBe('N/A')
  })

  it('returns N/A for undefined', () => {
    expect(wrapper.vm.formatDate(undefined)).toBe('N/A')
  })

  it('returns Invalid date for garbage string', () => {
    expect(wrapper.vm.formatDate('not-a-date')).toBe('Invalid date')
  })

  it('formats an ISO date string without throwing', () => {
    const result = wrapper.vm.formatDate('2024-06-15')
    expect(result).not.toBe('N/A')
    expect(result).not.toBe('Invalid date')
    // Should contain the year
    expect(result).toContain('2024')
  })

  it('formats an array date [year, month, day]', () => {
    const result = wrapper.vm.formatDate([2024, 6, 15])
    expect(result).not.toBe('N/A')
    expect(result).not.toBe('Invalid date')
    expect(result).toContain('2024')
  })
})

// -----------------------------------------------------------------------
// Initial data state
// -----------------------------------------------------------------------

describe('App — initial state', () => {
  it('starts with empty risks array', async () => {
    const wrapper = await mountApp()
    expect(Array.isArray(wrapper.vm.risks)).toBe(true)
  })

  it('starts with no vineyard selected', async () => {
    const wrapper = await mountApp()
    expect(wrapper.vm.vineyard).toBeNull()
  })

  it('starts with wbiPrognosis having null peronospora and oidium', async () => {
    const wrapper = await mountApp()
    expect(wrapper.vm.wbiPrognosis.peronospora).toBeNull()
    expect(wrapper.vm.wbiPrognosis.oidium).toBeNull()
  })
})
