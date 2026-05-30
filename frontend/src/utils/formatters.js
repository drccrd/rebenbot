import { BBCH_STAGES } from './constants.js'

export function formatDate(dateValue) {
  if (!dateValue) return 'N/A'
  try {
    let date
    if (Array.isArray(dateValue)) {
      const [year, month, day] = dateValue
      date = new Date(year, month - 1, day)
    } else {
      date = new Date(dateValue)
    }
    if (isNaN(date.getTime())) return 'Invalid date'
    return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
  } catch (e) {
    return 'Invalid date'
  }
}

export function formatDateTime(isoString) {
  if (!isoString) return 'N/A'
  try {
    let date
    if (Array.isArray(isoString)) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = isoString
      date = new Date(year, month - 1, day, hour, minute, second)
    } else {
      date = new Date(isoString)
    }
    if (isNaN(date.getTime())) return 'Invalid date'
    return date.toLocaleString('de-DE', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', hour12: false
    })
  } catch (e) {
    return 'Invalid date'
  }
}

export function formatWbiDate(dateArray) {
  if (!dateArray) return 'N/A'
  try {
    if (Array.isArray(dateArray)) {
      const [year, month, day] = dateArray
      const date = new Date(year, month - 1, day)
      if (isNaN(date.getTime())) return 'Invalid date'
      return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    }
    return 'Invalid date'
  } catch (e) {
    return 'Invalid date'
  }
}

export function bbchLabel(code) {
  if (!code && code !== 0) return ''
  if (code >= 11 && code <= 19) return `${code - 10} lea${code - 10 === 1 ? 'f' : 'ves'} unfolded`
  if (code === 53) return 'Inflorescence clearly visible'
  if (code === 55) return 'Individual flowers visible'
  if (code === 57) return 'Flowers separating'
  const stage = [...BBCH_STAGES].reverse().find(s => s.value <= code)
  return stage ? stage.label : `BBCH ${code}`
}

export function isFutureEvent(datetimeArr) {
  if (!datetimeArr || !Array.isArray(datetimeArr)) return false
  const [year, month, day, hour = 0, minute = 0, second = 0] = datetimeArr
  return new Date(year, month - 1, day, hour, minute, second) > new Date()
}

export function urgencyClass(urgency) {
  return {
    'rec-urgent': urgency === 'URGENT',
    'rec-action': urgency === 'ACTION_RECOMMENDED',
    'rec-scheduled': urgency === 'SCHEDULED',
    'rec-monitor': urgency === 'MONITOR'
  }
}

export function formatOptimalConditions(disease) {
  const commonName = disease?.commonName || disease?.name || ''
  if (commonName.includes('Peronospora')) return '10-25°C + 85%+ humidity + wetness'
  if (commonName.includes('Oidium')) return '15-27°C + 40%+ humidity'
  return 'Check thresholds'
}
