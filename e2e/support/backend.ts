import { execFileSync, spawn, spawnSync, type ChildProcess } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Starts and stops the real backend jar so tests can restart it mid-run.
// Database settings come from E2E_DB_* (CI) or default to the local
// docker-compose PostgreSQL (localhost-only dev credentials, not secret).

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const TARGET = path.join(ROOT, 'backend', 'target');
// Its own port, so a running dev server (:8080) never interferes.
export const E2E_API_PORT = 18080;
const HEALTH = `http://localhost:${E2E_API_PORT}/actuator/health`;
const STATE_DIR = path.join(ROOT, 'e2e', '.state');
// The PID of the JVM a run started, so a crashed run's backend can be stopped next time.
const PID_FILE = path.join(STATE_DIR, 'backend.pid');
mkdirSync(STATE_DIR, { recursive: true });

let process_: ChildProcess | null = null;

export function backendJar(): string {
  const jar = readdirSync(TARGET).find((f) => /^shardbound-backend-.*\.jar$/.test(f) && !f.endsWith('-plain.jar'));
  if (!jar) throw new Error(`No backend jar in ${TARGET}. Build it first: cd backend; ./mvnw -DskipTests package`);
  return path.join(TARGET, jar);
}

// Plain node:http on purpose: polling a refused port with fetch (undici) every
// 250 ms crashed the Playwright worker on Windows now and then.
function healthy(): Promise<boolean> {
  return new Promise((resolve) => {
    const request = http.get(HEALTH, { timeout: 2_000 }, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk: string) => (body += chunk));
      response.on('end', () => resolve(response.statusCode === 200 && body.includes('"UP"')));
    });
    request.on('timeout', () => request.destroy());
    request.on('error', () => resolve(false));
  });
}

async function waitUntil(check: () => Promise<boolean>, timeoutMs: number, what: string): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await check()) return;
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`Timed out waiting for ${what}`);
}

// The JVM's own executable, never a launcher. On Windows `java` on the PATH
// can be Oracle's javapath shim, which starts the real JVM as a separate
// process that outlives a kill of the shim. JAVA_HOME wins; otherwise ask
// `java` itself where its home is.
let javaPath: string | null = null;
function javaExecutable(): string {
  if (javaPath) return javaPath;
  let home = process.env.JAVA_HOME;
  if (!home) {
    const result = spawnSync('java', ['-XshowSettings:properties', '-version'], { encoding: 'utf8' });
    home = /java\.home = (.+)/.exec(`${result.stdout}${result.stderr}`)?.[1]?.trim(); // the JVM prints settings to stderr
  }
  javaPath = home ? path.join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  return javaPath;
}

/** Starts the backend and resolves once /actuator/health is UP. */
export async function start(): Promise<void> {
  if (process_) throw new Error('backend already running');
  if (await healthy()) {
    throw new Error(`Something is already serving :${E2E_API_PORT}. Stop it before running the e2e tests.`);
  }
  const env = {
    ...process.env,
    SPRING_DATASOURCE_URL: process.env.E2E_DB_URL ?? 'jdbc:postgresql://localhost:5432/shardbound',
    SPRING_DATASOURCE_USERNAME: process.env.E2E_DB_USER ?? 'shardbound',
    SPRING_DATASOURCE_PASSWORD: process.env.E2E_DB_PASSWORD ?? 'shardbound-dev',
    PORT: String(E2E_API_PORT),
  };
  const child = spawn(javaExecutable(), ['-jar', backendJar()], { env, stdio: process.env.E2E_BACKEND_LOGS ? 'inherit' : 'ignore' });
  process_ = child;
  if (child.pid !== undefined) writeFileSync(PID_FILE, String(child.pid));
  child.on('exit', () => {
    if (process_ === child) process_ = null; // an older process exiting late must not forget the new one
  });
  await waitUntil(healthy, 90_000, 'the backend to report UP');
}

function killPid(pid: number): void {
  if (process.platform === 'win32') {
    execFileSync('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'ignore' });
  } else {
    process.kill(pid, 'SIGKILL');
  }
}

/** Stops the backend this module started (the JVM itself, see javaExecutable) and resolves once it no longer answers. */
export async function stop(): Promise<void> {
  const child = process_;
  if (!child || child.pid === undefined || child.exitCode !== null) return;
  killPid(child.pid);
  await waitUntil(async () => child.exitCode !== null || child.signalCode !== null, 30_000, 'the backend to exit');
  await waitUntil(async () => !(await healthy()), 30_000, 'the backend to stop answering');
  rmSync(PID_FILE, { force: true });
}

// True if `pid` is still a running process (signal 0 only checks; it sends nothing).
function isRunning(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

/**
 * Stops a backend left behind by an earlier run that crashed before it could
 * clean up. Only the exact JVM recorded in the PID file is touched, and only
 * while the e2e port still answers (so a reused PID is never killed).
 */
export async function stopLeftover(): Promise<void> {
  if (!existsSync(PID_FILE)) return;
  const pid = Number(readFileSync(PID_FILE, 'utf8'));
  if (Number.isInteger(pid) && isRunning(pid) && (await healthy())) {
    killPid(pid);
    await waitUntil(async () => !(await healthy()), 30_000, 'the leftover backend to stop');
  }
  rmSync(PID_FILE, { force: true });
}
