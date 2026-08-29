# Static verification record

This compatibility record exists for deterministic repository checks. It is not a current production-readiness or real-device test claim.

## 1. Scope

Static bridge, shell, telemetry, recovery, documentation, and workflow invariants.

## 2. Source identity

Application source must remain identical to the accepted source snapshot.

## 3. Contract

The bridge schema and adversarial fixtures are checked by repository tests.

## 4. Android

Native compilation and device behavior require their dedicated CI and device gates.

## 5. iOS

Native compilation and device behavior require macOS, Xcode, and device gates.

## 6. Security

Exact-origin, main-frame, deny-by-default, TLS, and dependency policies are statically inspected.

## 7. Privacy

The public data inventory defines the allowed classes and minimization rules.

## 8. Store evidence

Store metadata, review access, and real-device evidence remain release-time responsibilities.

## 9. Limitations

Static success does not prove signing, distribution, provider, or real-device behavior.

## 10. Next gate

Do not start Phase 6 until the separately defined release and device gates are complete.
