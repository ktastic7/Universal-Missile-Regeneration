# Universal Missile Regeneration

**Universal Missile Regeneration (UMR)** gives eligible finite-ammo missile launchers a combat-time ammunition regeneration system while preserving their normal firing behavior.

**Version:** 1.0.1  
**Author:** kemptastic  
**For:** Starsector 0.98a-RC8  
**Required mod dependencies:** None  
**Optional:** LunaLib, Version Checker  
**License:** PolyForm Noncommercial 1.0.0

Source and releases:

https://github.com/ktastic7/Universal-Missile-Regeneration

Starsector forum thread:

https://fractalsoftworks.com/forum/index.php?topic=36118

---

## What UMR does

Missile weapons in Starsector are normally limited by ammunition. UMR lets eligible finite-ammo missile launchers slowly regenerate ammunition during combat.

The goal is not to redesign missile weapons. UMR changes their **ammunition endurance**, while leaving their authored firing characteristics alone.

UMR applies the same regeneration rules to eligible player and NPC ships.

It also supports many ordinary modded missile launchers automatically through a common classifier rather than requiring a hard-coded whitelist for every mod.

### In practical terms

You should expect:

- ordinary finite-ammo missile launchers to recover ammunition over time;
- multi-shot launchers to regain ammunition in burst-sized packets rather than one missile at a time;
- firing more missiles during an active refill timer to **not** reset the progress already earned;
- Expanded Missile Racks and compatible max-ammo bonuses to increase how much UMR can refill;
- player and NPC ships to use the same core regeneration rules;
- launchers that already have native ammo regeneration to keep their authored regeneration instead of being double-managed;
- unusual scripted/special launchers to be excluded when UMR cannot safely treat them as ordinary finite-ammo missiles.

---

# Installation

1. Download the latest `Universal-Missile-Regeneration.zip` from the GitHub Releases page.
2. Extract the ZIP into your Starsector `mods` folder.
3. Confirm that the extracted folder contains `mod_info.json`.
4. Enable **Universal Missile Regeneration** in the Starsector launcher.
5. Start or load a campaign normally.

UMR does **not** require a new campaign.

LunaLib is optional. Version Checker is optional.

---

# What UMR changes by default

UMR manages ordinary ship-mounted weapons whose weapon type is `MISSILE` and that pass its safety/compatibility classifier.

An ordinary eligible launcher generally needs to:

- use ammunition;
- have a finite, defined base ammo capacity greater than zero;
- not already have native ammo regeneration;
- not be a system/helper weapon;
- not be marked as a restricted/special/no-standard-data case;
- have the timing data UMR needs;
- not be disabled by blacklist policy.

UMR also performs live-combat checks before managing a weapon.

It does not manage missile weapons on:

- fighters/bombers represented as fighter ships;
- drones;
- hulks;
- station modules.

It also excludes live weapons in:

- system weapon slots;
- decorative weapon slots.

---

# What UMR does **not** intentionally modify

UMR is designed to leave the weapon itself alone.

It does not normally change:

- damage;
- projectile speed;
- tracking;
- range;
- OP cost;
- authored base burst size;
- target selection;
- weapon AI hints;
- ship AI personality;
- fire/no-fire commands;
- facing;
- native weapon cooldown;
- charge state;
- authored native ammo regeneration.

UMR's combat intervention is the controlled restoration of ammunition when a refill completes, plus the documented Missile Autoloader compatibility behavior below.

---

# How ammunition regeneration works

## Refill packets preserve the launcher's burst

By default, the refill packet is the weapon's **authored base burst size**.

Examples:

- a single-shot launcher normally refills 1 missile at a time;
- a 2-shot burst launcher normally refills 2 at a time;
- a 4-shot burst launcher normally refills 4 at a time.

This is intentional. UMR tries to return a usable authored salvo instead of slowly trickling individual missiles back into a burst weapon.

## When does the timer start?

UMR waits until at least **one complete refill packet is missing**.

If a launcher has a default refill packet of 4 and is missing only 1–3 missiles, that deficit alone does not start a new refill chain.

Once a refill chain is active, however, the final refill at the ammo cap may be smaller than the normal packet when necessary.

For example, a 50-ammo weapon with a 4-ammo refill packet may refill:

`... 40 → 44 → 48 → 50`

The final `+2` is a normal cap/remainder top-off.

## Firing again does not reset progress

If you fire additional missiles while a UMR timer is already running:

- the timer does not restart;
- the progress you already earned is preserved;
- the remaining ammo deficit simply grows.

## External ammo additions do not erase progress

If another game mechanic adds ammo during an active UMR timer, UMR does not blindly apply an old precomputed value.

At timer completion it re-reads:

- current ammo;
- current maximum ammo.

This allows it to preserve earned progress without overfilling the weapon.

---

# Default regeneration timing

UMR starts from a target time to regenerate the weapon's **authored/base magazine**.

Default nominal full-base-magazine targets are:

| Weapon size | Base max ammo = 1 | Base max ammo > 1 |
| --- | ---: | ---: |
| SMALL | 70 sec | 90 sec |
| MEDIUM | 95 sec | 115 sec |
| LARGE | 115 sec | 135 sec |

These come from the default global settings:

- base full-magazine target: `90` seconds;
- single-ammo reduction: `20` seconds;
- MEDIUM addition: `25` seconds;
- LARGE addition: `45` seconds.

For the default authored refill packet:

```text
number of base refill packets = ceil(base max ammo / base burst size)

nominal packet interval =
    adjusted full-magazine target / number of base refill packets
```

The safety floor described below can make the real interval longer.

---

# Safety floor

UMR enables a refill safety floor by default.

Default:

```text
umrSafetyFloorEnabled = true
umrSafetyMultiplier = 1.4
```

UMR calculates an authored/base firing-cycle value from the weapon's original timing data.

The refill packet interval is not allowed to become shorter than:

```text
authored firing cycle × safety multiplier
```

If that safety value is slower than the normal magazine-based interval, the safety value wins.

This keeps very fast-firing burst launchers from regenerating full packets at an implausibly aggressive rate simply because of their magazine math.

---

# Expanded Missile Racks and other ammo-capacity bonuses

UMR calculates its base timing using the weapon's **original authored data**.

During combat, however, UMR uses the weapon's live `maxAmmo` as the refill ceiling.

That means Expanded Missile Racks and compatible modded bonuses that increase missile capacity can be refilled by UMR.

Important distinction:

> More ammo capacity does **not** make the refill interval faster.

If a weapon's capacity is increased, UMR may have more packets to restore. Those extra packets use the same established interval instead of compressing the larger magazine into the original base-magazine target.

If maximum ammo changes during combat, UMR follows the new live ceiling without recalculating the base timing from the bonus.

---

# Native-regenerating and special weapons

UMR does not add a second regeneration system to a launcher whose original weapon data already provides positive native ammo regeneration.

It also conservatively excludes classes such as:

- system/helper weapons;
- infinite/undefined-ammo weapons;
- restricted/special/no-standard-data cases;
- weapons missing required timing data.

Some heavily scripted modded weapons may not be fully describable by the normal WeaponSpec data UMR uses.

For those cases UMR provides:

- local blacklist support;
- local timing/packet overrides;
- third-party compatibility contribution CSVs;
- detailed logging.

---

# Modded missile support

UMR enables eligible third-party missile support by default:

```json
"umrThirdPartyGameplayEnabled": true
```

There is no requirement for a permanent per-mod whitelist.

Many ordinary modded finite-ammo missile launchers can therefore work automatically.

UMR was tested during development with a large modded environment, including observed refill behavior from multiple third-party weapon packs.

That does **not** mean every unusual scripted launcher in every mod is guaranteed to behave like an ordinary finite-ammo weapon.

If you encounter one that should not be managed—or needs different timing—the CSV compatibility system is designed for exactly that case.

To keep UMR active for vanilla weapons but disable source-attributed third-party missile enrollment:

```json
"umrThirdPartyGameplayEnabled": false
```

---

# Missile Autoloader compatibility

Starsector's vanilla **Missile Autoloader** hullmod is handled specially because otherwise a UMR-managed launcher could receive ammunition from both systems.

## Combat behavior

For UMR-managed live weapons, UMR suppresses normal Missile Autoloader refill behavior for that weapon.

This prevents double-dipping.

UMR does not solve this by globally deleting Missile Autoloader or globally modifying every missile weapon spec.

An unmanaged/blacklisted weapon can retain ordinary vanilla Autoloader behavior where applicable.

## Player refit convenience

By default, UMR hides Missile Autoloader from the ordinary **Add Hullmod** picker.

This does **not**:

- remove the player's unlock/known state;
- remove an already installed copy;
- stop an installed copy from being visible/removable;
- globally erase the hullmod from the game.

With LunaLib installed, this can be toggled with:

**Hide Missile Autoloader**

Default: ON.

## NPC random-autofit convenience

By default, UMR also suppresses an NPC Missile Autoloader copy when it can conservatively prove that the copy was randomly generated by the normal fleet/autofit process and would be wasted on UMR-managed behavior.

UMR preserves:

- built-in copies;
- permanent copies;
- S-mod copies;
- copies authored on the original variant;
- ambiguous/unresolved cases.

With LunaLib installed, this can be toggled with:

**Suppress NPC Random Missile Autoloader**

Default: ON.

---

# LunaLib

LunaLib is **optional**.

If LunaLib is installed, UMR exposes these settings in LunaLib's mod-settings UI:

| Setting | Default | Effect |
| --- | --- | --- |
| Hide Missile Autoloader | ON | Hides Missile Autoloader from the ordinary player add-hullmod picker. |
| Suppress NPC Random Missile Autoloader | ON | Removes only provably random generated NPC Autoloader copies under the conservative generation rules. |

If LunaLib is not installed:

- UMR still loads normally;
- missile regeneration still works;
- both convenience policies use their shipped default of ON;
- there is simply no LunaLib UI for changing them.

---

# `settings.json`

File:

```text
data/config/settings.json
```

The following are the effective public/diagnostic controls in 1.0.0.

## `umrThirdPartyGameplayEnabled`

Default:

```json
true
```

Allows eligible source-attributed third-party missile weapons to be managed.

Set to `false` for vanilla-only eligible missile management.

---

## `umrBaseFullMagTargetSeconds`

Default:

```json
90.0
```

Base full-magazine target before size/single-ammo adjustments.

---

## `umrSingleAmmoReductionSeconds`

Default:

```json
20.0
```

Reduction applied when the weapon's authored base maximum ammo is exactly 1.

---

## `umrMediumAdditionSeconds`

Default:

```json
25.0
```

Added to the target time for MEDIUM missile weapons.

---

## `umrLargeAdditionSeconds`

Default:

```json
45.0
```

Added to the target time for LARGE missile weapons.

---

## `umrSafetyFloorEnabled`

Default:

```json
true
```

Master switch for the firing-cycle safety floor.

---

## `umrSafetyMultiplier`

Default:

```json
1.4
```

Multiplier applied to the authored firing-cycle value for the safety floor.

---

## `umrLoggingLevel`

Default:

```json
"SUMMARY"
```

Accepted values:

- `"OFF"`
- `"SUMMARY"`
- `"VERBOSE"`
- `"TRACE"`

See [Logging](#logging-and-bug-reports) below.

---

## `umrPerformanceTelemetryEnabled`

Default:

```json
false
```

Enables read-only UMR performance telemetry.

This does not change regeneration behavior.

Leave it off for normal play unless collecting diagnostics.

---

## `umrAiTelemetryEnabled`

Default:

```json
false
```

Enables read-only regenerated-ammo AI-use telemetry.

This does not change AI hints, targeting, or firing behavior.

Leave it off for normal play unless collecting diagnostics.

---

## `umrEnableNonMissileLimitedAmmo`

Default:

```json
false
```

This is a reserved/deferred placeholder.

**Finite-ammo non-missile regeneration is not a supported 1.0.0 feature.**

Leave this setting `false`.

Changing it to `true` should not be expected to activate functional non-missile regeneration in 1.0.0.

---

## Internal fields

You may also see internal identity/plugin fields such as:

- `plugins`
- `umrDevelopmentVersion`
- `umrDevelopmentPhase`
- `umrDevelopmentCore`

These are not player tuning controls.

---

# Configuration precedence

When multiple configuration sources apply, UMR uses this authority order:

1. hard/native/system/helper exclusion;
2. local user blacklist;
3. local user override;
4. third-party blacklist contribution;
5. third-party override contribution;
6. global settings;
7. defaults.

A lower layer never overrides a higher layer.

---

# Local weapon blacklist

File:

```text
data/config/umr/weapon_blacklist.csv
```

Header:

```csv
weapon_id,reason,source_or_comment
```

Use this when you want a specific otherwise eligible weapon to remain completely unmanaged by UMR.

Example:

```csv
weapon_id,reason,source_or_comment
example_missile,Prefer original finite ammo behavior,Personal compatibility preference
```

## Columns

### `weapon_id`

Exact Starsector weapon ID.

### `reason`

Human-readable reason for the blacklist.

### `source_or_comment`

Optional note or provenance.

Local blacklist authority is absolute over UMR's ordinary timing/override layers.

---

# Local weapon overrides

File:

```text
data/config/umr/weapon_overrides.csv
```

Header:

```csv
weapon_id,burst_or_packet_size_override,full_mag_target_seconds_override,packet_regen_seconds_override,safety_multiplier_override,disable_safety_floor,reason,source_or_comment
```

Example — direct packet interval:

```csv
weapon_id,burst_or_packet_size_override,full_mag_target_seconds_override,packet_regen_seconds_override,safety_multiplier_override,disable_safety_floor,reason,source_or_comment
example_missile,,,30.0,,,Custom refill interval,Personal tuning
```

Example — custom packet and full-mag target:

```csv
weapon_id,burst_or_packet_size_override,full_mag_target_seconds_override,packet_regen_seconds_override,safety_multiplier_override,disable_safety_floor,reason,source_or_comment
example_barrage,3,120.0,,,,Custom packet and target,Personal tuning
```

## Columns

### `weapon_id`

Exact Starsector weapon ID.

### `burst_or_packet_size_override`

Optional integer refill packet size.

Blank = UMR's normal authored/default packet.

### `full_mag_target_seconds_override`

Optional replacement full-base-magazine target for this weapon.

### `packet_regen_seconds_override`

Optional direct packet interval.

If both this field and a full-mag target are present on the same effective row, the direct packet interval controls the nominal interval.

### `safety_multiplier_override`

Optional per-weapon safety multiplier.

### `disable_safety_floor`

Optional Boolean.

Use `true` to disable the safety floor for this weapon.

A blank/false value does not force-enable a globally disabled floor.

### `reason`

Human-readable explanation.

### `source_or_comment`

Optional provenance or note.

---

# Third-party mod integration CSVs

UMR also ships lower-authority compatibility contribution files intended for mod authors or compatibility packs.

Local player configuration still wins over these files.

## Third-party blacklist

File:

```text
data/config/umr/third_party_blacklist.csv
```

Header:

```csv
row_id,weapon_id,source_mod_id,reason
```

Example:

```csv
row_id,weapon_id,source_mod_id,reason
my_mod_special_01,my_mod_special_launcher,my_mod_id,Script manages launcher ammo directly
```

### `row_id`

Unique row/contribution ID.

### `weapon_id`

Exact target weapon ID.

### `source_mod_id`

Expected Starsector mod ID that owns the weapon.

### `reason`

Compatibility explanation.

---

## Third-party override

File:

```text
data/config/umr/third_party_overrides.csv
```

Header:

```csv
row_id,weapon_id,source_mod_id,burst_or_packet_size_override,full_mag_target_seconds_override,packet_regen_seconds_override,safety_multiplier_override,disable_safety_floor,reason
```

Example:

```csv
row_id,weapon_id,source_mod_id,burst_or_packet_size_override,full_mag_target_seconds_override,packet_regen_seconds_override,safety_multiplier_override,disable_safety_floor,reason
my_mod_barrage_01,my_mod_barrage,my_mod_id,4,,20.0,,,Authored compatibility timing
```

These fields have the same basic timing/packet meaning as the local override file, but third-party rows have lower authority than the player's local blacklist/override files.

---

# Version Checker

UMR 1.0.0 includes Version Checker-compatible metadata.

**Version Checker is optional.** UMR does not require it to run.

If you use a compatible update checker or mod manager, UMR's metadata can provide:

- the current public version;
- the target Starsector version;
- a direct GitHub Release download;
- the public changelog;
- the Starsector forum thread after that thread is created and added to the remote master metadata.

UMR uses:

```text
universal_missile_regeneration.version
data/config/version/version_files.csv
```

The online master is hosted in the public GitHub repository.

# Save compatibility and removal

UMR 1.0.0 does **not require a new campaign**.

Release testing covered:

- loading existing saves;
- saving and reloading;
- starting a new game;
- clean Starsector 0.98a-RC8 + UMR;
- a large modded campaign environment.

UMR's core combat regeneration state is combat-instance state rather than a persistent ammo-regeneration object that your campaign depends on.

Removal was also validated using a disposable save copy: the campaign loaded and saved again with UMR disabled without UMR missing-class/deserialization failures.

That said, changing any Starsector mod list is worth treating carefully.

**Back up important saves before adding/removing mods.**

---

# Logging and bug reports

UMR writes a dedicated diagnostic log:

```text
UniversalMissileRegeneration.log
```

The exact location is alongside Starsector's normal log output for the running installation.

## OFF

Disables routine UMR dedicated diagnostic logging.

## SUMMARY — default

Recommended for normal play.

Provides bounded information such as:

- UMR build/session identity;
- configuration summary;
- registry/enrollment totals;
- battle summaries;
- meaningful warnings/errors.

It avoids routine per-refill spam.

## VERBOSE

Recommended when:

- reporting a specific modded launcher;
- contributing compatibility data;
- testing a large mod set for UMR;
- investigating ammo/refill behavior.

Adds weapon-level details such as:

- managed candidates;
- refill events;
- weapon provenance;
- configuration authority;
- external ammo events;
- ammo-cap changes;
- timer start/continue information.

## TRACE

Deep timing/state diagnostics.

Includes VERBOSE information plus more detailed timer-progress information.

TRACE can create much larger logs. Use it temporarily when actively diagnosing a specific issue.

---

# Contributing large-mod-set data

Large mod sets are especially useful for finding unusual launchers that a normal test environment may never encounter.

If you want to help UMR compatibility work:

1. Use UMR normally under `SUMMARY` first.
2. If you want to provide weapon-level data, change:
   ```json
   "umrLoggingLevel": "VERBOSE"
   ```
3. Play normal campaign/simulation battles with your mod set.
4. Submit:
   - UMR version;
   - Starsector version;
   - mod list or relevant mod names/versions;
   - `UniversalMissileRegeneration.log`;
   - `starsector.log`;
   - any weapon/mod you specifically noticed.
5. Say whether you are reporting a visible problem or simply contributing compatibility telemetry.

Use `TRACE` only if deeper timing information is specifically needed.

### Privacy note

Logs can contain environment or filesystem path information.

Before uploading logs publicly, inspect them for usernames, filesystem paths, or other information you do not want to share.

---

# Troubleshooting

## A launcher is not regenerating

Possible reasons include:

- the weapon is not classified as a normal finite-ammo MISSILE weapon;
- it already has native ammo regeneration;
- it is a special/system/helper weapon;
- it is on a fighter/drone/station-module context UMR excludes;
- it is blacklisted;
- third-party gameplay is disabled;
- the launcher has not yet lost a complete refill packet;
- required timing data is unavailable.

Try:

1. set logging to `VERBOSE`;
2. reproduce the battle;
3. inspect/submit the dedicated UMR log.

## A modded launcher behaves strangely

Do not immediately assume it needs code.

The preferred compatibility order is:

1. determine whether it should be excluded as a general class;
2. blacklist if authored behavior should remain untouched;
3. override timing/packet behavior if regeneration is safe;
4. use targeted compatibility code only if declarative configuration cannot express the requirement.

## LunaLib is not installed

That is fine.

UMR does not require LunaLib.

Only the in-game toggles for the two Missile Autoloader convenience policies are unavailable.

## I do not want modded missiles affected

Set:

```json
"umrThirdPartyGameplayEnabled": false
```

Vanilla eligible missiles remain managed.

## I want to disable UMR completely

Disable the mod in the Starsector launcher.

---

# Technical overview

At application load, UMR:

1. scans missile WeaponSpecs;
2. resolves provenance/source mod information;
3. applies hard/native exclusions;
4. applies local and third-party policy;
5. computes a validated timing entry for each managed weapon.

During combat, UMR:

1. observes eligible live weapons;
2. creates per-weapon timer state when a full refill packet is missing;
3. advances that state without altering authored firing behavior;
4. re-reads current ammo/max ammo when the timer completes;
5. restores one packet, bounded by the live ammo ceiling;
6. continues the chain while another full deficit remains;
7. records bounded diagnostics according to the selected logging level.

UMR also intercepts the live Missile Autoloader cooldown-tracker path for UMR-managed weapons to prevent double refill systems from stacking.

It does not replace ship AI.

---

# Performance

Pre-release profiling found UMR's normal `SUMMARY`-mode combat overhead to be well below 1% of a 60 FPS frame budget in the tested battles, including large modded engagements.

Performance varies by hardware, battle size, logging level, and mod environment, so this is a development measurement rather than a universal fixed guarantee.

If investigating performance, keep in mind that `VERBOSE` and especially `TRACE` intentionally do more logging than the normal release default.

---

# Planned / under consideration

## Finite-ammo non-missile support

A future opt-in system for finite-ammo non-missile weapons has been explored but is **not included in UMR 1.0.0**.

There is no promised release date.

## Ongoing compatibility work

Future updates may include:

- compatibility fixes driven by real player/mod-author reports;
- contributed blacklist/override rules;
- documentation/usability improvements;
- compatibility updates for future Starsector versions.

Feedback is welcome.

---

# Bug reports, compatibility reports, and feature ideas

GitHub Issues:

https://github.com/ktastic7/Universal-Missile-Regeneration/issues

The repository includes dedicated issue forms for:

- bugs;
- compatibility reports;
- field telemetry/data contributions;
- feature requests.

See also:

[CONTRIBUTING.md](CONTRIBUTING.md)

and:

[COMPATIBILITY.md](COMPATIBILITY.md)

---

# Building from source

See:

[BUILDING.md](BUILDING.md)

UMR's 1.0.0 release is promoted from a baseline compiled with `javac --release 8` against the Starsector 0.98a-RC8 API.

The game itself uses Java 17 on this Starsector baseline.

---

# License

UMR is source-available under the **PolyForm Noncommercial License 1.0.0**.

In short, the license permits noncommercial use, modification, and redistribution under its terms, including the required attribution/notice obligations.

The complete authoritative terms are in:

[LICENSE](LICENSE)

Required Notice:

`Required Notice: Copyright 2026 kemptastic. Universal Missile Regeneration.`

---

# Credits

**Author:** kemptastic

Universal Missile Regeneration was developed through extensive vanilla, modded, save-compatibility, performance, and field-telemetry testing against Starsector 0.98a-RC8.

Thank you to players and mod authors who report unusual compatibility cases or contribute field data.
