# FoodHome bridge contract 1.6.0 source handoff

The canonical source is the `bridge-contract/` Git tree in the public
`arsenshahaev005-sudo/foodhome-app` repository. The package remains private to npm;
this handoff publishes immutable source through Git, not an npm registry.

## Immutable identity

The initial reviewed source commit is
`5f4398dfd71266d073b87847d785e67b90148743`. Before production integration,
resolve the accepted merged commit, verify that its `bridge-contract/` tree is
identical, and pin that full merge commit downstream. Never substitute a branch,
working-tree snapshot, or an unrelated base commit.

## Checksums

`foodhome-bridge-contract-1.6.0.sha256` lists all 67 files in the committed
`bridge-contract/` tree, sorted lexicographically. Digests are SHA-256 over exact
Git blob bytes, not a Windows working-tree checkout. The manifest is UTF-8 with LF
line endings and a final newline. Its SHA-256 is:

`7bee33097fca5fd5c1a70f125642f4237ac2c76137ab78304c9a3804f332466f`

The package tarball has a separate digest and must be reproduced from the accepted
source with `npm pack --ignore-scripts`; never reuse the source-manifest digest as
the tarball digest.

## Compatibility and activation

Version 1.6.0 remains bridge major 1 and adds Android-only
`openNotificationSettings`. Request payload is strictly empty and native dispatch
requires a recent foreground user action. Existing push, payment, browser, iOS and
fallback behavior remains unchanged. Publication does not enable push flags,
provider delivery, binding, payment, or automatic settings changes.
