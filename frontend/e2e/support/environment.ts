import { existsSync, realpathSync, statSync } from "node:fs";
import { isAbsolute, relative, resolve } from "node:path";

const repositoryRoot = realpathSync(resolve(__dirname, "../../.."));

function externalStorageState(variable: string, fallback?: string): string | undefined {
  const configured = process.env[variable] ?? (fallback ? process.env[fallback] : undefined);
  if (!configured) return undefined;

  const absolute = isAbsolute(configured) ? configured : resolve(process.cwd(), configured);
  if (!existsSync(absolute)) {
    throw new Error(`${variable} points to a missing file: ${absolute}`);
  }
  const canonical = realpathSync(absolute);
  if (!statSync(canonical).isFile()) {
    throw new Error(`${variable} must point to a regular storage-state file.`);
  }
  const repositoryRelative = relative(repositoryRoot, canonical);
  if (!repositoryRelative.startsWith("..") && !isAbsolute(repositoryRelative)) {
    throw new Error(`${variable} must point outside the repository because storage state contains credentials.`);
  }
  return canonical;
}

function httpUrl(variable: string, fallback?: string): string | undefined {
  const configured = process.env[variable] ?? fallback;
  if (!configured) return undefined;
  let url: URL;
  try {
    url = new URL(configured);
  } catch {
    throw new Error(`${variable} must be an absolute HTTP(S) URL.`);
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error(`${variable} must use HTTP or HTTPS.`);
  }
  return url.toString().replace(/\/$/, "");
}

export const e2eEnvironment = {
  baseUrl: httpUrl("ROTRACK_E2E_BASE_URL", "http://localhost:3000")!,
  userAStorageState: externalStorageState(
    "ROTRACK_E2E_USER_A_STORAGE_STATE",
    "ROTRACK_E2E_STORAGE_STATE",
  ),
  userBStorageState: externalStorageState("ROTRACK_E2E_USER_B_STORAGE_STATE"),
  requireAuth: process.env.ROTRACK_E2E_REQUIRE_AUTH === "1",
};

if (
  e2eEnvironment.requireAuth &&
  (!e2eEnvironment.userAStorageState || !e2eEnvironment.userBStorageState)
) {
  throw new Error(
    "ROTRACK_E2E_REQUIRE_AUTH=1 requires User A and User B storage-state paths. See e2e/README.md.",
  );
}
