// Starts the Spring Boot backend in the dev profile, which also starts
// docker-compose.yml (PostgreSQL + Mailpit). Used by `npm run dev`.
// Picks the Maven wrapper that fits the operating system.
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const backend = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'backend');
const windows = process.platform === 'win32';

// The wrapper is called by its absolute path: Windows shells may be set up not
// to look in the current directory. On Windows a .cmd needs a shell, and the
// whole command goes in as one quoted string (Node rejects args + shell).
const wrapper = path.join(backend, windows ? 'mvnw.cmd' : 'mvnw');
const args = ['-q', 'spring-boot:run', '-Dspring-boot.run.profiles=dev'];
const child = windows
  ? spawn(`"${wrapper}" ${args.join(' ')}`, { cwd: backend, stdio: 'inherit', shell: true })
  : spawn(wrapper, args, { cwd: backend, stdio: 'inherit' });

child.on('exit', (code) => process.exit(code ?? 0));
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal));
}
