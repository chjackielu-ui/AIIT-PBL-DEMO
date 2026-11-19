/** 同源 HTTP 入口；页面和状态管理共用，后端接口接入统一放在这里。 */
export async function request(path: string, options: RequestInit = {}): Promise<Response> {
  try {
    return await fetch(path, {credentials: 'same-origin', ...options, signal: options.signal ?? AbortSignal.timeout(30000)})
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw Error('无法连接服务，请确认服务已启动后重试')
  }
}

export async function api<T = any>(path: string, body?: unknown, method?: string): Promise<T> {
  const hasBody = body !== undefined
  const res = await request('/api' + path, {
    method: method ?? (hasBody ? 'POST' : 'GET'),
    headers: body instanceof FormData ? {} : {'Content-Type': 'application/json'},
    body: body instanceof FormData ? body : hasBody ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let message = '请求失败'
    try { message = (await res.json()).message || message } catch {}
    throw Error(message)
  }
  return res.status === 204 ? undefined as T : res.json()
}
