import { Buffer } from "node:buffer";

export const SAFE_REMOTE_CONFIG = Object.freeze({
  source: "safe-default",
  baseWebEnabled: true,
  maintenanceEnabled: false,
  updateMode: "none",
  enabledCapabilities: Object.freeze([]),
  paymentFlowVersion: "disabled",
});

export function isMessageWithinLimit(message, maxMessageBytes) {
  return Buffer.byteLength(message, "utf8") <= maxMessageBytes;
}

const FORBIDDEN_JSON_KEYS = new Set(["__proto__", "constructor", "prototype"]);

export function isJsonStructureSafe(value, { maxDepth, maxNodes }) {
  if (!Number.isInteger(maxDepth) || maxDepth < 1) return false;
  if (!Number.isInteger(maxNodes) || maxNodes < 1) return false;

  let visited = 0;
  const pending = [{ value, depth: 1 }];
  while (pending.length > 0) {
    const current = pending.pop();
    visited += 1;
    if (visited > maxNodes || current.depth > maxDepth) return false;
    if (current.value === null || typeof current.value !== "object") continue;

    const entries = Array.isArray(current.value)
      ? current.value.map((item, index) => [String(index), item])
      : Object.entries(current.value);
    for (const [key, child] of entries) {
      if (FORBIDDEN_JSON_KEYS.has(key)) return false;
      pending.push({ value: child, depth: current.depth + 1 });
    }
  }
  return true;
}

export function resolveRemoteConfig({
  config,
  schemaValid,
  now,
  compiledCapabilities,
  builtInCapabilities = compiledCapabilities,
  providerPolicyCapabilities = builtInCapabilities,
}) {
  if (!config || !schemaValid) return SAFE_REMOTE_CONFIG;

  const issuedAt = Date.parse(config.issuedAt);
  const expiresAt = Date.parse(config.expiresAt);
  const nowValue = now instanceof Date ? now.getTime() : Date.parse(now);

  if (
    !Number.isFinite(issuedAt) ||
    !Number.isFinite(expiresAt) ||
    !Number.isFinite(nowValue) ||
    issuedAt > nowValue ||
    expiresAt <= nowValue
  ) {
    return SAFE_REMOTE_CONFIG;
  }

  const builtIn = builtInCapabilities ?? new Set();
  const providerPolicy = providerPolicyCapabilities ?? new Set();
  const enabledCapabilities = [...builtIn]
    .filter((capability) => providerPolicy.has(capability))
    .filter((capability) => config.capabilities[capability] === true)
    .sort();
  const requestedPaymentFlow = config.payment?.flowVersion ?? "disabled";
  const paymentFlowVersion =
    requestedPaymentFlow !== "disabled" && enabledCapabilities.includes("openPayment")
      ? requestedPaymentFlow
      : "disabled";

  return Object.freeze({
    source: "validated-config",
    baseWebEnabled: true,
    maintenanceEnabled: config.maintenance.enabled,
    updateMode: config.update.mode,
    enabledCapabilities: Object.freeze(enabledCapabilities),
    paymentFlowVersion,
  });
}

export class TerminalResponseGuard {
  #completed = new Set();

  complete(requestId) {
    if (this.#completed.has(requestId)) return false;
    this.#completed.add(requestId);
    return true;
  }
}
