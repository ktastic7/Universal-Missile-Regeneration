Universal Missile Regeneration 1.0.0 — configuration files

Full player documentation is in the mod root README.md.

Local player files:
- weapon_blacklist.csv
- weapon_overrides.csv

Third-party compatibility contribution files:
- third_party_blacklist.csv
- third_party_overrides.csv

Configuration authority, highest to lowest:
1. hard/native/system/helper exclusion
2. local player blacklist
3. local player override
4. third-party blacklist
5. third-party override
6. global settings
7. defaults

weapon_blacklist.csv
Header:
weapon_id,reason,source_or_comment

weapon_overrides.csv
Header:
weapon_id,burst_or_packet_size_override,full_mag_target_seconds_override,packet_regen_seconds_override,safety_multiplier_override,disable_safety_floor,reason,source_or_comment

third_party_blacklist.csv
Header:
row_id,weapon_id,source_mod_id,reason

third_party_overrides.csv
Header:
row_id,weapon_id,source_mod_id,burst_or_packet_size_override,full_mag_target_seconds_override,packet_regen_seconds_override,safety_multiplier_override,disable_safety_floor,reason

Notes:
- Local player configuration outranks third-party contributions.
- Hard/native exclusions cannot be bypassed with an override.
- Malformed rows fail soft where possible and are reported in the UMR log.
- Default logging is SUMMARY.
- For weapon-level compatibility data, use VERBOSE.
- TRACE is intended for targeted deep investigation.
- LunaLib is optional.
- Finite-ammo non-missile regeneration is not a supported 1.0.0 feature.
