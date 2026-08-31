<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="t('ai.title')"
    direction="rtl"
    size="420px"
    class="ai-drawer"
    @open="onOpen"
  >
    <div class="ai-layout">
      <div class="ai-toolbar">
        <el-select v-model="providerId" style="flex: 1" :placeholder="t('ai.selectProvider')" @change="onProviderChange">
          <el-option
            v-for="p in providers"
            :key="p.id"
            :label="providerLabel(p)"
            :value="p.id"
          />
        </el-select>
        <el-tooltip :content="t('ai.settingsTip')" placement="bottom">
          <el-button :icon="Setting" circle @click="showConfig = !showConfig" />
        </el-tooltip>
      </div>

      <el-collapse-transition>
        <div v-show="showConfig" class="ai-config">
          <el-form label-position="top" size="small">
            <el-form-item :label="t('ai.apiKey')">
              <el-input v-model="apiKey" type="password" show-password :placeholder="t('ai.apiKeyPlaceholder')" clearable />
            </el-form-item>
            <el-form-item :label="t('ai.baseUrl')">
              <el-input v-model="baseUrl" :placeholder="t('ai.baseUrlPlaceholder')" clearable />
            </el-form-item>
            <el-form-item :label="t('ai.model')">
              <el-input v-model="model" :placeholder="t('ai.modelPlaceholder')" clearable />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="includeContext">{{ t('ai.includeContext') }}</el-checkbox>
            </el-form-item>
            <el-button type="primary" size="small" @click="persistSettings">{{ t('ai.saveLocal') }}</el-button>
          </el-form>
          <p class="ai-hint">{{ t('ai.keyHint') }}</p>
        </div>
      </el-collapse-transition>

      <div ref="chatBoxRef" class="ai-messages">
        <div v-if="messages.length === 0" class="ai-empty">
          <p>{{ t('ai.welcome') }}</p>
          <div class="ai-quick">
            <el-button
              v-for="q in quickQuestions"
              :key="q"
              size="small"
              plain
              @click="ask(q)"
            >{{ q }}</el-button>
          </div>
        </div>
        <div
          v-for="(m, idx) in messages"
          :key="idx"
          class="ai-msg"
          :class="m.role"
        >
          <div class="ai-bubble">{{ m.content }}</div>
        </div>
        <div v-if="streaming" class="ai-msg assistant">
          <div class="ai-bubble streaming">{{ streamBuffer || '…' }}</div>
        </div>
      </div>

      <div class="ai-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          :placeholder="t('ai.inputPlaceholder')"
          @keydown.enter.exact.prevent="send"
        />
        <div class="ai-actions">
          <el-button link type="danger" :disabled="streaming || messages.length === 0" @click="clearChat">{{ t('ai.clear') }}</el-button>
          <el-button type="primary" :loading="streaming" :disabled="!input.trim()" @click="send">{{ t('ai.send') }}</el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, nextTick, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import request from '@/api'
import { loadAiSettings, saveAiSettings } from '@/utils/aiSettings'

defineProps({ modelValue: Boolean })
defineEmits(['update:modelValue'])

const { t, locale } = useI18n()

const providers = ref([])
const providerId = ref('ollama')
const apiKey = ref('')
const baseUrl = ref('')
const model = ref('')
const includeContext = ref(true)
const showConfig = ref(false)
const input = ref('')
const messages = ref([])
const streaming = ref(false)
const streamBuffer = ref('')
const chatBoxRef = ref(null)
const aiEnabled = ref(true)

const quickQuestions = computed(() => {
  if (locale.value === 'en') {
    return [
      'Who is using port 8080?',
      'Any port conflicts right now?',
      'Suggest free ports for a Spring Boot app'
    ]
  }
  return [
    '谁占用了 8080？',
    '当前有没有端口冲突？',
    '给 Spring Boot 推荐几个空闲端口'
  ]
})

function providerLabel(p) {
  const mark = p.configured ? '' : ` (${t('ai.needKey')})`
  return `${p.displayName}${mark}`
}

function loadLocal() {
  const s = loadAiSettings()
  providerId.value = s.providerId || 'ollama'
  apiKey.value = s.apiKey || ''
  baseUrl.value = s.baseUrl || ''
  model.value = s.model || ''
  includeContext.value = s.includeContext !== false
}

function persistSettings(silent = false) {
  saveAiSettings({
    providerId: providerId.value,
    apiKey: apiKey.value,
    baseUrl: baseUrl.value,
    model: model.value,
    includeContext: includeContext.value
  })
  if (!silent) ElMessage.success(t('ai.savedLocal'))
}

function onProviderChange() {
  const p = providers.value.find((x) => x.id === providerId.value)
  if (p) {
    // 切换厂商时同步服务端预设，避免 LocalStorage 里旧 baseUrl/model 串台
    baseUrl.value = p.baseUrl || ''
    model.value = p.model || ''
  }
  persistSettings(true)
}

async function onOpen() {
  loadLocal()
  try {
    const res = await request.get('/ai/status')
    aiEnabled.value = !!res.data?.enabled
    providers.value = res.data?.providers || []
    const serverDefault = res.data?.defaultProvider || 'ollama'
    const current = providers.value.find((x) => x.id === providerId.value)
    const needMigrate = !providerId.value
      || (providerId.value !== serverDefault
        && current?.needsApiKey
        && !apiKey.value
        && !current?.configured)
    if (needMigrate) {
      providerId.value = serverDefault
    }
    const p = providers.value.find((x) => x.id === providerId.value)
    if (p) {
      // 始终与当前厂商服务端配置对齐 baseUrl/model（Key 仍用本地）
      if (!baseUrl.value || needMigrate) baseUrl.value = p.baseUrl || ''
      if (!model.value || needMigrate) model.value = p.model || ''
    }
    persistSettings(true)
    if (!aiEnabled.value) {
      ElMessage.warning(t('ai.disabled'))
    }
  } catch {
    providers.value = []
  }
}

function buildBody(text) {
  // 当前这条 user 已 push 进 messages，历史里不要重复带上
  const history = messages.value
    .slice(0, -1)
    .filter((m) => m.role === 'user' || m.role === 'assistant')
    .slice(-8)
    .map((m) => ({ role: m.role, content: m.content }))
  return {
    providerId: providerId.value,
    message: text,
    includeContext: includeContext.value,
    apiKey: apiKey.value || undefined,
    baseUrl: baseUrl.value || undefined,
    model: model.value || undefined,
    history
  }
}

async function scrollBottom() {
  await nextTick()
  const el = chatBoxRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function ask(q) {
  input.value = q
  send()
}

function clearChat() {
  messages.value = []
  streamBuffer.value = ''
}

function parseSseDataLine(line) {
  // SSE: "data:" 后最多一个可选空格，不要 trim 全文（避免吃掉有意义空格）
  if (!line.startsWith('data:')) return null
  let data = line.slice(5)
  if (data.startsWith(' ')) data = data.slice(1)
  if (data.endsWith('\r')) data = data.slice(0, -1)
  return data
}

async function chatSync(text) {
  const res = await request.post('/ai/chat', buildBody(text))
  return (res.data?.content || '').trim()
}

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return
  persistSettings(true)
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  streaming.value = true
  streamBuffer.value = ''
  await scrollBottom()

  try {
    const res = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(buildBody(text))
    })
    if (!res.ok) {
      const errText = await res.text()
      throw new Error(errText || `HTTP ${res.status}`)
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let eventName = 'message'
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n')
      buffer = parts.pop() || ''
      for (const raw of parts) {
        const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const data = parseSseDataLine(line)
          if (data == null) continue
          if (eventName === 'error') {
            throw new Error(data || 'AI stream error')
          }
          if (eventName === 'done' || data === '[DONE]') {
            eventName = 'message'
            continue
          }
          if (data.length) {
            streamBuffer.value += data
            await scrollBottom()
          }
          eventName = 'message'
        } else if (line === '') {
          eventName = 'message'
        }
      }
    }
    let content = (streamBuffer.value || '').trim()
    // 流式成功但内容为空时，回退同步接口（兼容部分代理/网关）
    if (!content) {
      content = await chatSync(text)
    }
    messages.value.push({ role: 'assistant', content: content || t('ai.emptyReply') })
  } catch (e) {
    try {
      const content = await chatSync(text)
      messages.value.push({ role: 'assistant', content: content || t('ai.emptyReply') })
    } catch (e2) {
      const detail = e2.response?.data?.message || e2.message || e.message || ''
      messages.value.push({ role: 'assistant', content: `${t('ai.error')}: ${detail}` })
    }
  } finally {
    streaming.value = false
    streamBuffer.value = ''
    await scrollBottom()
  }
}
</script>

<style scoped>
.ai-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 10px;
}
.ai-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.ai-config {
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.ai-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 4px 2px;
  min-height: 240px;
}
.ai-empty {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}
.ai-quick {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  align-items: flex-start;
}
.ai-msg {
  margin-bottom: 10px;
  display: flex;
}
.ai-msg.user {
  justify-content: flex-end;
}
.ai-bubble {
  max-width: 92%;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-msg.user .ai-bubble {
  background: #667eea;
  color: #fff;
}
.ai-msg.assistant .ai-bubble {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}
.ai-bubble.streaming {
  opacity: 0.9;
}
.ai-input {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 10px;
}
.ai-actions {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
