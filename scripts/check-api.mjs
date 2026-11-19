import {spawn} from 'node:child_process'
import {mkdtemp, mkdir, rm, open} from 'node:fs/promises'
import {resolve} from 'node:path'
import {setTimeout as delay} from 'node:timers/promises'

const runtime = resolve('.runtime')
await mkdir(runtime, {recursive: true})
const directory = await mkdtemp(resolve(runtime, 'check-'))
const log = await open(resolve(directory, 'server.log'), 'a')
let server
let address

async function start() {
  // 随机端口 + 独立数据目录；不连接或停止已有演示服务。
  server = spawn(process.argv[2] || 'java', ['-jar', resolve('backend/target/project-platform-1.0.0.jar'),
    '--server.address=127.0.0.1', '--server.port=0', '--app.storage=test-json', `--app.data-dir=${directory}/data`],
    {windowsHide: true, stdio: ['ignore', 'pipe', log.fd]})
  let output = ''
  server.stdout.on('data', chunk => {
    log.write(chunk).catch(() => {})
    output += chunk
    const port = output.match(/Tomcat started on port (\d+)/)
    if (port) address = `http://127.0.0.1:${port[1]}/api`
  })
  let error
  server.on('error', e => { error = e })
  for (let attempt = 0; attempt < 120; attempt++) {
    if (error) throw error
    if (server.exitCode !== null) throw Error(`服务启动失败，查看 ${directory}/server.log`)
    if (address) {
      try { if ((await fetch(address + '/health', {signal: AbortSignal.timeout(1000)})).ok) return } catch {}
    }
    await delay(500)
  }
  throw Error(`服务启动超时，查看 ${directory}/server.log`)
}

async function stop() {
  if (!server || server.exitCode !== null) return
  const closed = new Promise(resolve => server.once('close', resolve))
  server.kill()
  await closed
  address = undefined
}

async function run(script) {
  const child = spawn(process.execPath, [`scripts/${script}.mjs`], {
    env: {...process.env, API_URL: address}, windowsHide: true, stdio: 'inherit',
  })
  await new Promise((resolve, reject) => {
    child.on('error', reject)
    child.on('exit', code => code === 0 ? resolve() : reject(Error(`${script} 失败 (${code})`)))
  })
}

let passed = false
try {
  await start()
  for (const script of ['verify-api', 'verify-discussion', 'verify-student-upgrade', 'verify-presentation']) await run(script)
  await stop()
  await start()
  await run('verify-persistence')
  passed = true
  console.log('PASS 隔离接口回归及服务重启持久化')
} finally {
  await stop()
  await log.close()
  if (passed) await rm(directory, {recursive: true})
  else console.error(`失败现场保留：${directory}`)
}
