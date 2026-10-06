# Changelog

All notable public changes to Universal Missile Regeneration are documented here.

The pre-1.0 internal development chronology is intentionally not reproduced in this public changelog.

## Version 1.0.0

### Added

- Combat-time ammunition regeneration for eligible finite-ammo missile launchers.
- Common vanilla and third-party missile eligibility/classification.
- Burst/salvo-aware refill packets based on authored base burst size.
- Configurable global full-magazine timing model.
- Configurable firing-cycle safety floor.
- Runtime maximum-ammo support, including Expanded Missile Racks-style capacity bonuses.
- Non-resetting refill progress when additional missiles are fired during an active timer.
- Safe coexistence with external ammo additions during a UMR timer.
- Local per-weapon blacklist CSV.
- Local per-weapon timing/packet override CSV.
- Lower-authority third-party blacklist and override contribution CSVs for mod integration.
- Dedicated UMR logging with `OFF`, `SUMMARY`, `VERBOSE`, and `TRACE` levels.
- Optional read-only performance and AI telemetry.
- Missile Autoloader double-dip prevention for UMR-managed live weapons.
- Optional-LunaLib controls for:
  - hiding Missile Autoloader from the ordinary refit add-hullmod picker;
  - suppressing provably random NPC Missile Autoloader autofit copies.
- Version Checker-compatible release metadata.
- Save/load and removal compatibility validation for the 1.0.0 release baseline.
- GitHub issue/report templates for bugs, compatibility, field telemetry, and feature requests.

### Compatibility

- LunaLib is optional.
- Version Checker is optional.
- No third-party Starsector mod library is required for core UMR gameplay.
- Eligible source-attributed third-party missile launchers are enabled by default.
- Native-regenerating, helper/system, infinite/undefined-ammo, and special/restricted/no-standard-data cases are conservatively preserved/excluded.
- Fighters, drones, hulks, station modules, system weapon slots, and decorative slots are excluded from live UMR management.

### Defaults

- Public logging level: `SUMMARY`.
- Third-party eligible missile support: enabled.
- Safety floor: enabled.
- Safety multiplier: `1.4`.
- Phase 9 performance telemetry: disabled.
- Phase 9 AI telemetry: disabled.
- Finite-ammo non-missile regeneration: not included in 1.0.0.
- Missile Autoloader refit-picker convenience: enabled.
- NPC random-Autoloader suppression convenience: enabled.

### Notes

UMR 1.0.0 does not require a new campaign.

Removal from a disposable save was validated during release hardening, but players should still back up important saves before changing any Starsector mod list.
