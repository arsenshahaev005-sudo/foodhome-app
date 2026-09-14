# FoodHome bridge contract 1.7.0 source handoff

Canonical source: `bridge-contract/` in `arsenshahaev005-sudo/foodhome-app`.
This is a Git source publication, not an npm registry release.

## Immutable identity

Initial source commit: `d4d022c2575ea9157781d2e92df453e0dbe8f175`.
Contract subtree: `7764da052f5329a5e84c3179308633553df273d6`.

Before production integration, resolve the accepted merged commit, verify its
contract subtree and all checksums, then pin the full merge commit downstream.
Never use a branch name or unrelated base commit as immutable provenance.

## Checksums

`foodhome-bridge-contract-1.7.0.sha256` contains 68 lexicographically sorted
paths, hashed over exact Git blob bytes, not Windows checkout/CRLF bytes.
The checksum manifest is UTF-8 with LF and one final newline. Its SHA-256 is:

`8a762cce220a51f9ec2b9420cda4308598272c6175016e7113698318ed2cc0f7`

An npm tarball has its own digest; build it from the verified Git blobs using
`npm pack --ignore-scripts`. Do not confuse source-manifest and tarball hashes.
The historical 1.5.0 and 1.6.0 manifests are unchanged.

## Compatibility and release boundary

Android 0.2.5/build 7 adds the explicit `seller.order.new` application payload v2
event. Bridge major 1 and existing methods remain unchanged. The sender MUST gate
the event to compatible installations and use the previous `order.updated` for
old/unknown clients; old APKs reject the new enum. No private copy or remote sound
URL is accepted. Sound and presentation remain native.

[Integration and rollout task](../integration/food-home-seller-order-sound-1.7.md).
Publishing/merging source does not deploy food-home, enable provider flags, grant
notification permissions, install an APK, or prove audible device delivery.
