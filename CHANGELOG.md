# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## 0.2.0

### Sound layer

Every sound below is synthesised from scratch by `tools/generate_sounds.py`
(`numpy` + `scipy` → `ffmpeg`/libvorbis). No file is a sample from Mojang or
any other mod. Spells intentionally remain silent — Hex Casting already voices
the cast itself.

#### Added

- **Chimera** got a voice. `chimera_ambient` (3 variants), `chimera_hurt` (2),
  `chimera_death`, `chimera_step` (2), `chimera_headbutt`. The voice is built
  as a source-filter model with three parallel formant banks (cow, pig, sheep)
  crossfaded *inside a single utterance*, so the creature audibly slips from
  one parent to the other mid-breath. `sizeFactor` scales both volume and
  pitch, so a small chimera sounds smaller.
- **Amethyst Heart** now beats, sings while soaking media, chimes on every new
  dust that crystallises, protests when a breeding it was chosen for cannot
  fit, and calls out when it spits out a piece of Resonant Meat.
  (`amethyst_heart_beat`, `_charge`, `_crystallise`, `_overflow`, `_resonate`.)
- **Vow Altar** has four sounds across the whole rite: `vow_altar_offer`
  (a ring settling into its slot), `vow_altar_ritual` (a five-second braid of
  two voices converging on unison — the length matches `RITUAL_TICKS` exactly),
  `vow_altar_vow` (the seal), `vow_altar_sever` (the reverse).
- **World Soul Altar** has a full arc: `world_soul_awaken` (the courtyard
  completing), `world_soul_{attune,detach,embody}_start`, mid-ritual events
  (`world_soul_attune_seal`, `world_soul_detach_break`, `world_soul_embody_forge`),
  their finish chords (`world_soul_{attune,detach,embody}_finish`), and
  `world_soul_ritual_fail` for genuine aborts. Finish chords play on the
  visual burst, not on the item landing, because they outlive it.
- **Crystallized Affection** now sounds like it is opened by magic, not
  broken. A shared `crystallized_affection_burst` plays as the seal unsnaps
  (soft warm inrush, not a shatter), followed by a tier-specific answer:
  `crystallized_affection_open_silence`, `_open_spark`, `_open_world`,
  `_open_echo`, `_open_legacy`. There is also `crystallized_affection_strain`
  played on the held-use tick with a rising pitch.

Subtitles for every event are present in English (via `en_us.flatten.json5`)
and Russian. Every sound is registered in `HexloveSounds.kt` and
`assets/hexlove/sounds.json`.

### Changed

- **The world link now belongs to the soul, not to the ring.** Every
  Soulbound Ring bound to the same player shares one attunement history and
  one accumulated affection pool, both stored in
  `HexloveWorldData.soulWorldMemories`. Rings carry a snapshot of the charge
  in NBT so tooltips and the client model keep working; the server always
  reads the authoritative value from world data. Legacy saves migrate on the
  first altar interaction or on the next join/respawn/dimension change; the
  migration only ever raises the pool, never lowers it.

  This fixes two bugs at once:

  - unlinking one ring and attuning a fresh one no longer re-grants the
    24 000-tick `worlds_embrace` bonus;
  - a player carrying two world-linked rings no longer accumulates two
    parallel charge pools.

  Marriage on multiple rings already worked before this (via
  `MarriageManager.synchronizeRings`) — no change was needed for that side.

- **Crystallized Affection tier weights rebalanced** from `4 / 30 / 21 / 42 / 3`
  to `4 / 30 / 20 / 40 / 6`. Legendary tier is now 6 %; the extra 3 %
  comes from 1 % of the blue tier and 2 % of the purple tier.

- **Purple-tier reward filter** blacklists `hexcasting:lore_fragment`. A
  bare lore fragment has no payoff on its own, and the up-to-32 re-roll
  loop was getting stuck on it.

- **Book copy** now describes opening a Crystallized Affection as coaxing it
  with the magic that made it, not shattering it. English and Russian
  copies updated in `assets/hexlove/lang/`.

- **Chimera parent blend** is no longer a strict average.
  `Chimera.initializeFromParents` centres the child on the parents' mean
  and then applies a small two-tailed multiplicative scatter, so most
  chimeras are typical but rare births come out as ant-sized runts or
  oversized horrors. Size and health scatter are independent, so a huge
  chimera can still be surprisingly fragile — and vice-versa. Configurable
  through `breeding.chimeraSizeNoise` (default 0.18) and
  `breeding.chimeraHealthNoise` (default 0.22). Setting either to zero
  restores the previous strict-average behaviour. Cross-species and
  chimera×chimera breeding both use the same scatter.

- **Config schema.** `breeding.chimeraSizeNoise` and
  `breeding.chimeraHealthNoise` added. No `configVersion` bump — existing
  TOML files inherit the defaults from the constructor at load time.

- **`MarriageManager.refresh` moved to a 20-tick cadence** (down from every
  tick). Every consumer of "is this marriage active" still verifies rings
  live at the point of use, so nothing becomes stale.

### Fixed

- Ritual failure and ritual finish are now distinct sound paths.
  `WorldSoulAltarBlockEntity.failRitual` plays the fail sound on real
  aborts (structure broken, ring guard failed, player left range); the
  three finish paths intentionally call `cancelRitual` in silence so the
  finish chord and the fail chord never overlap.

### New advancement

- `hexlove:humiliated` (challenge). Awarded when a farm animal you fed
  under Serenity leads you all the way to your humiliation payoff.

## 0.1.0

### Added

- Mod created.
