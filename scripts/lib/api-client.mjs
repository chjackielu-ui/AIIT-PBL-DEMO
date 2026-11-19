import assert from 'node:assert/strict'

// 写入检查必须由调用方显式选择服务；npm run check 自动提供隔离实例。
export const origin = process.env.API_URL
assert(origin, '请使用 npm run check，或显式设置指向隔离服务的 API_URL')

export function client() {
  let cookie = ''
  return async (path, body, expected = 200) => {
    const res = await fetch(origin + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: {...(body instanceof FormData ? {} : {'Content-Type': 'application/json'}), Cookie: cookie},
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    })
    if (res.headers.get('set-cookie')) cookie = res.headers.get('set-cookie').split(';')[0]
    const raw = await res.text()
    assert.equal(res.status, expected, `${path}: ${raw}`)
    try { return JSON.parse(raw) } catch { return raw }
  }
}
