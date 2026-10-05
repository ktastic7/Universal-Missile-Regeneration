# Contributing to Universal Missile Regeneration

Feedback, compatibility reports, field data, documentation corrections, and feature ideas are welcome.

UMR's public repository is primarily a distribution/source/support surface. Internal project governance and release authority are maintained separately by the project.

## Before opening an issue

Please check:

- that you are using the current UMR release;
- that the report is actually associated with UMR;
- whether you changed `settings.json` or any UMR CSVs;
- whether a specific weapon/mod is involved.

## Bug reports

Use the **Bug report** issue form.

Please include:

- UMR version;
- Starsector version;
- relevant mod list;
- steps to reproduce;
- expected behavior;
- actual behavior;
- UMR settings/CSV changes;
- `UniversalMissileRegeneration.log`;
- `starsector.log`.

## Compatibility reports

Use the **Compatibility report** form for a particular weapon or mod interaction.

If possible, use `VERBOSE` logging for the reproduction.

Do not assume a strange scripted launcher necessarily requires code. UMR can often handle an exception through blacklist or override configuration.

## Field telemetry / large mod sets

If you have a large mod set and want to help compatibility work even without a visible bug, use the **Field telemetry / data contribution** form.

Recommended:

```json
"umrLoggingLevel": "VERBOSE"
```

Play normal campaign/simulation battles, then submit the resulting UMR/Starsector logs and relevant mod information.

SUMMARY is useful for broad health data; VERBOSE is preferred for weapon-level compatibility data.

TRACE should only be used when a specific investigation calls for deep timer/state diagnostics.

## Feature requests

Use the **Feature request** form.

Please explain the player problem/use case first, rather than only proposing an implementation.

Features listed as "under consideration" are not promises or scheduled work.

## Mod-author compatibility contributions

Mod authors may propose rows for:

- `third_party_blacklist.csv`;
- `third_party_overrides.csv`.

Please include:

- source mod ID/version;
- weapon ID;
- reason;
- any relevant script/native ammo behavior;
- the requested UMR treatment.

Player-local configuration remains higher authority than contributed third-party rows.

## Code contributions

If you want to contribute code, opening an issue first is recommended so the intended behavior and validation scope can be agreed on.

UMR is distributed under the PolyForm Noncommercial License 1.0.0.

Do not submit code or assets you do not have the right to contribute.

## Privacy

Starsector logs can contain local filesystem/environment information.

Before publicly attaching logs, inspect them for user names, paths, or other information you do not want to share.
