import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { backendJar, stopLeftover } from './backend';

// Locally: make sure the compose PostgreSQL is up. In CI a service container
// provides it. Either way the backend jar must already be built.
export default async function globalSetup(): Promise<void> {
  backendJar(); // fails early with a clear message if the jar is missing
  await stopLeftover(); // a previous run that crashed may have left its backend running
  if (!process.env.CI) {
    execSync('docker compose up -d --wait postgres', { cwd: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..'), stdio: 'inherit' });
  }
}
