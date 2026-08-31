/**
 * AI 助手前端配置（存 LocalStorage，API Key 不落服务端）
 */
import { loadFromStorage, saveToStorage, STORAGE_KEYS } from './storage'

export function getDefaultAiSettings() {
  return {
    providerId: 'openai',
    apiKey: '',
    baseUrl: '',
    model: '',
    includeContext: true
  }
}

export function loadAiSettings() {
  return { ...getDefaultAiSettings(), ...loadFromStorage(STORAGE_KEYS.AI, getDefaultAiSettings()) }
}

export function saveAiSettings(settings) {
  saveToStorage(STORAGE_KEYS.AI, settings)
}
