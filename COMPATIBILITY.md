# Compatibility

Universal Missile Regeneration is designed to support ordinary finite-ammo missile launchers through a common rule set rather than a permanent mod-by-mod whitelist.

## Broad compatibility model

UMR normally manages a launcher when it:

- is a `MISSILE` weapon;
- uses finite defined ammo;
- has base ammo greater than zero;
- does not already have native ammo regeneration;
- is not a helper/system weapon;
- is not restricted/special/no-standard-data;
- has the timing data UMR needs;
- is not suppressed by local/third-party policy.

Live UMR management also excludes fighters, drones, hulks, station modules, system slots, and decorative slots.

## Third-party missiles

Third-party enrollment is enabled by default.

```json
"umrThirdPartyGameplayEnabled": true
```

Set it to `false` if you want UMR to manage eligible vanilla missiles only.

A modded weapon does not need to appear on an allowlist simply because it comes from another mod.

## Unusual scripted weapons

Some launchers use scripted/resource ammo systems that cannot be inferred reliably from normal WeaponSpec values.

If one of those behaves incorrectly with UMR, the preferred resolution order is:

1. improve the general classifier if the unsafe category is recognizable;
2. blacklist the weapon if it should preserve its authored ammo behavior;
3. add a timing/packet override if regeneration is safe but the defaults are unsuitable;
4. use targeted compatibility code only when configuration cannot express the requirement.

## Local authority

Configuration precedence:

1. hard/native/system/helper exclusion;
2. local user blacklist;
3. local user override;
4. third-party blacklist;
5. third-party override;
6. global settings;
7. defaults.

This means a player can always override lower-authority mod-integration configuration with local policy.

## Reporting compatibility problems

Please use the **Compatibility report** GitHub issue form.

Include:

- UMR version;
- Starsector version;
- affected mod/version;
- affected weapon ID/name;
- settings/CSV modifications;
- what you expected;
- what happened;
- `UniversalMissileRegeneration.log`;
- `starsector.log`.

`VERBOSE` logging is preferred when submitting a launcher-specific compatibility report.

## Contributing compatibility data

Large mod sets are useful even when nothing appears visibly broken.

Use the **Field telemetry / data contribution** issue form if you want to share session data.

For detailed weapon-level data:

```json
"umrLoggingLevel": "VERBOSE"
```

TRACE is intended for targeted deep investigation and can create much larger logs.

Before posting logs publicly, inspect them for filesystem/user-name information you do not want to share.

## Save compatibility

UMR 1.0.0 does not require a new campaign.

Release hardening validated:

- existing-save load;
- save/reload;
- new game;
- clean vanilla + UMR;
- large modpack use;
- removal from a disposable save.

Back up important saves before changing any Starsector mod list.
