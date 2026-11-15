---
name: pippyplayer-release-regressions
description: Verify PippyPlayer releases and published playback regressions. Use when preparing or publishing a PippyPlayer release, after publication, or when investigating a regression in a released APK. Fix confirmed regressions and publish a verified patch in the same task.
---

# PippyPlayer release regressions

Run from the PippyPlayer repository. Follow [AGENTS.md](../../../AGENTS.md) for GitHub release operations and [DEPLOY.md](../../../DEPLOY.md) for packaging, signing, and upgrade testing.

## Verify every release

1. Identify the previous known-good release and the candidate tag or commit. Review their diff to select the affected journeys. For player, overlay, or playback changes, run the [playback comparison](references/playback-comparison.md) before publishing. Run the Android tests, lint, and controller suite in [docs/building.md](../../../docs/building.md).
2. After publishing, continue in the same task. Use `gh release view` to confirm the intended stable release and `gh release download` to fetch its APK and checksum. Compare the downloaded bytes to the exact tested APK. Verify the app ID, version code, tag, and signing identity. Smoke-test the downloaded APK for upgrade, controller startup, and playback on the designated test device. Reuse the pre-publication evidence when the APK hashes match.
3. Record the baseline and candidate versions, what actually ran, the device, and any failing check. If the checks pass, finish. Keep physical-TV coverage distinct from emulator coverage. Use available devices and tests to keep diagnosing when physical hardware is unavailable.

## Resolve and ship a verified patch

1. Reproduce the failure against the published APK and compare it with the previous release using the same input and settings. Trace the changed code to the cause. A failing check on both builds calls for diagnosis before treating it as a release regression.
2. Fix the smallest part of the released behavior responsible for the regression. Add an automated check that distinguishes the broken and corrected behavior when feasible. Rerun the failing scenario and the affected regression suites. The published APK is the control for the fix, and the last known-good APK is the performance reference.
3. The project's standing instruction authorizes committing and publishing a verified patch for a confirmed release regression in the same task. Update the changelog, bump the patch version and Android version code, build with the existing signing identity, and test the exact signed APK. Commit the scoped changes, push the release commit and tag, and publish using `gh release` with the verified APK and checksum. Follow the same post-publication checks for the patch.
4. If a check still fails, continue diagnosing locally. Publish another patch only when evidence shows that it fixes a confirmed regression. If the fix or compatibility cannot be verified with the available evidence, report the specific blocker and preserve the local work. If `gh` fails, stop the release operation and report the failure as required by the project instructions.
