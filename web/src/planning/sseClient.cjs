'use strict'

function createSseParser(onEvent) {
  let buffer = ''
  let eventName = ''
  let eventId = ''
  let dataLines = []

  function dispatch() {
    if (dataLines.length === 0) {
      eventName = ''
      return
    }
    onEvent({
      id: eventId,
      event: eventName || 'message',
      data: dataLines.join('\n')
    })
    eventName = ''
    dataLines = []
  }

  function processLine(line) {
    if (line === '') {
      dispatch()
      return
    }
    if (line.startsWith(':')) return
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    let value = separator < 0 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') eventName = value
    else if (field === 'data') dataLines.push(value)
    else if (field === 'id' && !value.includes('\0')) eventId = value
  }

  return {
    push(chunk) {
      buffer += chunk
      let newline
      while ((newline = buffer.indexOf('\n')) >= 0) {
        let line = buffer.slice(0, newline)
        buffer = buffer.slice(newline + 1)
        if (line.endsWith('\r')) line = line.slice(0, -1)
        processLine(line)
      }
    },
    end() {
      if (buffer) {
        processLine(buffer.endsWith('\r') ? buffer.slice(0, -1) : buffer)
        buffer = ''
      }
      dispatch()
    }
  }
}

function isTerminalStatus(status) {
  return ['COMPLETED', 'NEEDS_ADJUSTMENT', 'FAILED'].includes(status)
}

function createPlanningStreamController(options) {
  const {
    taskId,
    url = `/api/planning-tasks/${encodeURIComponent(taskId)}/events`,
    accessToken,
    fetchImpl = globalThis.fetch,
    getTask,
    onEvent = () => {},
    onTaskStatus = () => {},
    onMalformedEvent = () => {},
    onError = () => {},
    onConnectionState = () => {},
    schedule = (fn, delay) => setTimeout(fn, delay),
    cancelSchedule = id => clearTimeout(id),
    maxReconnects = 5,
    baseDelayMs = 500,
    maxDelayMs = 8000
  } = options || {}

  if (!taskId) throw new Error('taskId is required')
  if (!accessToken) throw new Error('accessToken is required')
  if (typeof fetchImpl !== 'function') throw new Error('fetch implementation is required')
  if (typeof getTask !== 'function') throw new Error('getTask is required')

  let stopped = false
  let lastEventId = ''
  let reconnectCount = 0
  let timerId = null
  let abortController = null
  let reader = null

  function terminate(state) {
    stopped = true
    if (timerId != null) cancelSchedule(timerId)
    if (abortController) abortController.abort()
    if (reader) Promise.resolve(reader.cancel()).catch(() => {})
    onConnectionState(state)
  }

  function processFrame(frame) {
    let parsed
    try {
      parsed = JSON.parse(frame.data)
    } catch (_) {
      onMalformedEvent(new Error('无法解析规划进度事件'))
      return
    }
    if (frame.id !== '') lastEventId = frame.id
    onEvent({ id: frame.id, event: frame.event, data: parsed })
    if (frame.event === 'planning-status' && isTerminalStatus(parsed.status)) terminate('completed')
  }

  function fatalHttpError(error) {
    return error && [401, 403, 404].includes(error.status)
  }

  async function responseError(response) {
    let apiError = null
    try {
      const envelope = await response.json()
      apiError = envelope && envelope.error
    } catch (_) {
      apiError = null
    }
    const error = new Error(response.status === 401
      ? '登录状态已失效，请重新登录。'
      : (apiError?.message || '规划进度连接失败。'))
    error.status = response.status
    error.code = response.status === 401
      ? 'AUTHENTICATION_REQUIRED'
      : (apiError?.code || `SSE_HTTP_${response.status}`)
    error.fields = apiError?.fields || {}
    return error
  }

  async function scheduleReconnect() {
    if (stopped) return
    onConnectionState('checking')
    let task
    try {
      task = await getTask(taskId)
      onTaskStatus(task)
    } catch (error) {
      task = null
      if (fatalHttpError(error)) {
        onError(error)
        terminate('disconnected')
        return
      }
    }
    if (stopped) return
    if (task && isTerminalStatus(task.status)) {
      terminate('completed')
      return
    }
    if (reconnectCount >= maxReconnects) {
      onConnectionState('disconnected')
      return
    }
    const delay = Math.min(maxDelayMs, baseDelayMs * (2 ** reconnectCount))
    reconnectCount += 1
    onConnectionState('reconnecting')
    timerId = schedule(() => connect(), delay)
  }

  async function connect() {
    if (stopped) return
    abortController = new AbortController()
    const headers = {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${accessToken}`
    }
    if (lastEventId !== '') headers['Last-Event-ID'] = lastEventId
    onConnectionState('connecting')
    try {
      const response = await fetchImpl(url, {
        method: 'GET',
        headers,
        signal: abortController.signal,
        credentials: 'same-origin',
        cache: 'no-store'
      })
      if (!response.ok) throw await responseError(response)
      if (!response.body || typeof response.body.getReader !== 'function') throw new Error('SSE_STREAM_UNAVAILABLE')
      onConnectionState('connected')
      const parser = createSseParser(processFrame)
      const decoder = new TextDecoder()
      reader = response.body.getReader()
      while (!stopped) {
        const result = await reader.read()
        if (result.done) break
        parser.push(decoder.decode(result.value, { stream: true }))
      }
      parser.push(decoder.decode())
      parser.end()
      reader = null
      if (!stopped) await scheduleReconnect()
    } catch (error) {
      reader = null
      if (stopped || (error && error.name === 'AbortError')) return
      if (fatalHttpError(error)) {
        onError(error)
        terminate('disconnected')
        return
      }
      await scheduleReconnect()
    }
  }

  return {
    start: connect,
    stop() { terminate('stopped') },
    isStopped() { return stopped },
    getLastEventId() { return lastEventId }
  }
}

module.exports = {
  createPlanningStreamController,
  createSseParser,
  isTerminalStatus
}
