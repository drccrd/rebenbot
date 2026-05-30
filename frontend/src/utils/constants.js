// FRAC rotation slot definitions — based on official FRAC Working Group recommendations
export const PERON_SLOTS = [
  {
    id: 'P1', label: 'Slot P1',
    name: 'Multisite contact backbone',
    fracCodes: ['M1', 'M3', 'M4'],
    optional: false,
    rule: 'No FRAC resistance limit. Forms the backbone of every program — use on every spray not covered by other slots. Copper (M1), dithiocarbamates (M3), phthalimides (M4).',
    warning: null,
    ruleShort: 'No FRAC application limit (multisite contact)'
  },
  {
    id: 'P2', label: 'Slot P2',
    name: 'CAA fungicide — systemic, preventive',
    fracCodes: ['40'],
    optional: false,
    rule: 'FRAC CAA WG 2024: max 3–4 applications/season. Always apply preventively before expected rain. Always alternate with multisite contact partner.',
    warning: null,
    ruleShort: 'CAA WG: max 3–4/season, preventive before rain'
  },
  {
    id: 'P3', label: 'Slot P3',
    name: 'Phenylamide — systemic (optional, high pressure)',
    fracCodes: ['4'],
    optional: true,
    rule: 'FRAC Phenylamide EF 2020: max 2–4 applications/season, never more than 2 consecutive, always in mixture with a partner from a different FRAC group. Use only under high disease pressure.',
    warning: 'Resistance to FRAC 4 is documented in P. viticola populations in Germany. Use only when high disease pressure justifies it and always in mixture with a non-FRAC-4 partner.',
    ruleShort: 'Phenylamide EF: max 4/season, max 2 consecutive, always in mixture'
  },
  {
    id: 'P4', label: 'Slot P4',
    name: 'Phosphonate — systemic, late season (optional)',
    fracCodes: ['33'],
    optional: true,
    rule: 'No FRAC resistance limit. Systemic with curative activity. Useful as a late-season application after the CAA limit is reached, or following heavy infection periods.',
    warning: null,
    ruleShort: 'No FRAC application limit — systemic curative'
  }
]

export const OIDIUM_SLOTS = [
  {
    id: 'O1', label: 'Slot O1',
    name: 'Sulfur backbone',
    fracCodes: ['M2'],
    optional: false,
    rule: 'No FRAC resistance limit. Backbone for powdery mildew throughout the entire season. Do not apply above 28°C (phytotoxic risk, especially under high UV).',
    warning: null,
    ruleShort: 'No FRAC application limit — do not spray above 28°C'
  },
  {
    id: 'O2', label: 'Slot O2',
    name: 'DMI / triazole — sterol biosynthesis inhibitor',
    fracCodes: ['3'],
    optional: false,
    rule: 'FRAC SBI WG 2025: limit to max 50% of total oidium sprays per season. Max 3 consecutive applications of any SBI. Always alternate or mix with a non-SBI fungicide.',
    warning: null,
    ruleShort: 'SBI WG: max 50% of sprays, max 3 consecutive applications'
  },
  {
    id: 'O3', label: 'Slot O3',
    name: 'Amine / morpholine — SBI group (optional)',
    fracCodes: ['5'],
    optional: true,
    rule: 'FRAC SBI WG: cross-resistant with DMI (both belong to the SBI group). Use to replace one DMI application mid-season for rotation. Counts toward the SBI 50% total.',
    warning: null,
    ruleShort: 'SBI cross-resistance group — counts with DMI toward the 50% limit'
  },
  {
    id: 'O4', label: 'Slot O4',
    name: 'Quinoline — key rotation partner (optional)',
    fracCodes: ['13'],
    optional: true,
    rule: 'Key rotation partner for DMIs. Completely different mode of action — breaks SBI selection pressure. Registered specifically for powdery mildew. No cross-resistance with SBI group.',
    warning: null,
    ruleShort: 'FRAC 13 — no cross-resistance with DMI or amines'
  }
]

export const BBCH_STAGES = [
  { value: 0,  range: '00–09', label: 'Pre-budburst' },
  { value: 10, range: '10–19', label: 'Budburst' },
  { value: 25, range: '25–29', label: '5–9 leaves' },
  { value: 35, range: '35–39', label: 'Visible flower clusters' },
  { value: 45, range: '45–49', label: 'Bloom' },
  { value: 55, range: '55–59', label: 'Fruitset' },
  { value: 65, range: '65–69', label: 'Berries pea-sized' },
  { value: 75, range: '75–79', label: 'Véraison beginning' },
  { value: 81, range: '80–85', label: 'Post-véraison' },
  { value: 89, range: '89',    label: 'Harvest' },
]
