import { stopLeftover } from './backend';

// Teardown runs in its own process, so it cannot see the tests' live handle.
// It stops whatever backend the PID file still names, if the tests could not.
export default async function globalTeardown(): Promise<void> {
  await stopLeftover();
}
