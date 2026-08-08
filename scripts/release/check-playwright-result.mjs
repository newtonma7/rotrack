#!/usr/bin/env node
import { readFileSync } from "node:fs";

const [resultPath] = process.argv.slice(2);
if (!resultPath) {
  console.error("ERROR: pass the Playwright JSON result path");
  process.exit(1);
}

let result;
try {
  result = JSON.parse(readFileSync(resultPath, "utf8"));
} catch {
  console.error("ERROR: Playwright JSON result is missing or invalid");
  process.exit(1);
}

const stats = result.stats ?? {};
if (stats.expected !== 4 || stats.skipped !== 0 || stats.unexpected !== 0 || stats.flaky !== 0) {
  console.error(
    "ERROR: authenticated smoke must report exactly 4 expected passes and zero skipped, unexpected, or flaky tests",
  );
  process.exit(1);
}
console.log("PASS: authenticated Playwright result was exactly 4 passed, 0 skipped, 0 unexpected, 0 flaky");
