import { execFileSync } from 'node:child_process';

// The ports the app and Chrome use to reach Keycloak, the backend and the web server on the host (same numbers on both sides).
const REVERSED_PORTS = [18180, 18080, 18200];

/**
 * (Re)creates the `adb reverse` tunnels. They live in the host's adb server, and a restart of that server (which Appium may do when a
 * session starts) silently drops them: the emulator then gets ERR_CONNECTION_REFUSED for localhost. e2e/android-setup.sh creates them
 * once; this runs at the start of every session so a restart cannot leave a test without them.
 */
export function ensureReversedPorts(): void {
  for (const port of REVERSED_PORTS) {
    execFileSync('adb', ['reverse', `tcp:${port}`, `tcp:${port}`]);
  }
}
