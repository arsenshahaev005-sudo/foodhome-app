# FoodHome bridge contract 1.5.0 source handoff

The canonical source is the `bridge-contract/` Git tree in the public
`arsenshahaev005-sudo/foodhome-app` repository. The package remains private to npm:
this handoff publishes source through Git/PR, not an npm registry release.

## Immutable identity

Pin a full, remotely available commit SHA containing the 1.5.0 source and this
checksum manifest. Never substitute a branch name, a working-tree snapshot or the
base commit. The PR description records the initial published source commit.
Before production activation, resolve the accepted merged commit, verify the
checksums again and update downstream provenance; a PR is not merge acceptance.

The sibling repository may have a differently normalized candidate snapshot.
Compare its actual contents before replacing provenance. A hash mismatch is not
permission to label different bytes as identical.

## Checksums

`foodhome-bridge-contract-1.5.0.sha256` lists every file in the committed
`bridge-contract/` source tree, including package metadata, lockfile, schemas,
fixtures and tests. Paths are repository-relative, sorted lexicographically.
Digests use SHA-256 over the exact Git blob bytes, NOT a Windows working-tree
checkout with possible CRLF conversion. No client configuration or credential is
part of this tree.

The checksum file itself uses UTF-8, LF line endings, and a final newline. Its
SHA-256 is:

`011b98b4483c7bc0f66683dc96953d0dde3a16af3c46bf50987804bf41a55d31`

Obtain source bytes with Git object reads or an archive of the pinned commit.
For a package tarball, independently compute and record that tarball's SHA-256;
the source checksum above must not be relabeled as an npm tarball checksum.

## Activation remains separate

The artifact is bridge major 1, additive version 1.5.0. It does not enable native
push by itself. Android's `managePush` is advertised only by an explicitly enabled
configured binary; old Android, iOS and browser behavior retain their fallbacks.
The visible application payload is v2; existing silent v1 is not upgraded in place.

Publication does not establish server FCM authorization, accepted food-home
integration, a configured APK, real-device delivery or production readiness.
See [integration contract](../integration/android-visible-push-v2.md) and
[verification report](../reports/2026-09-09-android-push.md).
