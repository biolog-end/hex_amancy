# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added

- The Chimera is its own creature rather than one parent's body wearing a name tag. It keeps the
  blended health and speed of its parents and breeds true with other Chimeras. The look is a
  deliberate placeholder: vanilla cow geometry in a mottled green texture of our own.

### Changed

- Cupid's Charm is much stronger, and its strength of feeling finally means what the book says. A
  heart now resists a little under a quarter of a point per point of life, so two and a half holds a
  cow or a sheep forever and five holds a zombie, a creeper or a skeleton forever — comfortably
  forever, rather than five minutes short of it. Past five, feeling also widens what can be held at
  all, a little per point.
- A charmed creature keeps to its beloved the way a tamed wolf keeps to its master. It only joins a
  fight its beloved is actually in — struck somebody, or was struck, within the last few seconds and
  since the charm began — instead of setting off after whoever they traded blows with an hour ago. A
  chase that carries it more than twenty blocks from them ends: the grudge is dropped and it comes
  home.
- Blinding Jealousy guards out to six blocks.
- Bosses are refused outright rather than priced out: a heart of two hundred life or more is not
  stubborn, and no strength reaches it.
- A broken heart no longer stops anyone else from loving it. Grief closes the door outward only, so a
  heartbroken player can be charmed towards, guarded jealously, and sung to. Only courtship still
  needs two whole hearts.
- Blinding Jealousy follows the one it guards instead of standing where it was cast.
- Blinding Jealousy has a new pattern: a jagged scribble that doubles back on itself, in place of the
  old eye.
- The addon's book entries are one spread per spell: the rune on the left with its title and a note on
  what each iota is, the description facing it on the right. The entry opens with a short account of
  what Hexamancy is and how it was found.
- Config sections whose numbers changed meaning are reset once, on load, when the file predates the
  change. Prices are never touched.

### Fixed

- A charmed creature that is fighting for its beloved now finishes the fight. It used to stay glued
  to them and only swing at whatever wandered into arm's reach; now devotion steps aside while there
  is somebody to hit, and only pulls the creature back when it has strayed thirty-five blocks.
- Charmed slimes and magma cubes follow and defend. They have no usable pathfinder and a move control
  that ignores a wanted position, and the follow goal was also taking the movement slot their own
  jumping goal needs — so they stood still or hopped at random. They are now steered by their hop
  direction, and no longer hold that slot.
- Charmed slimes can now actually hurt what they chase. Vanilla only ever deals slime damage through
  the player-touch hook, so a slime has no way at all to injure another mob; it bounced off its target
  forever. It borrows the same strike the spell lends a cow.
- Cutting a charmed or jealous slime hands the feeling down to the pieces instead of leaving four
  indifferent slimes where one devoted slime stood.
- Charming two animals of different species starts a courtship again on worlds whose config file was
  written before Chimeras were switched on by default.
- Every refusal of Cupid's Charm and Blinding Jealousy now says what it actually is: grief, a boss, a
  strength too small for the creature, a union the server disallows, or a heart handed to itself.
  They all used to read "cannot alter" and name the wrong creature about half the time.
- Great Spells now get a shape in worlds that already existed when the mod was installed. Hex only
  fills its per-world pattern table when a world is created, so in an older world Cupid's Charm had
  no shape: it could not be cast, and holding an Ancient Scroll rolled for it crashed the server
  every tick. Missing shapes are added on world load; shapes already recorded are left alone, so
  Great Spells the players of that world have learned do not move.
  - One consequence of leaving them alone: a world that already had a shape for Cupid's Charm keeps
    the one derived from the pattern's earlier outline, not the pierced heart. Only worlds seeing the
    spell for the first time get a shape drawn from the current one.
- A scroll naming an action this world has no shape for — from an uninstalled addon, say — now goes
  blank with a line in the log instead of crashing the server. The guard no longer skips scrolls that
  already carry a resolved pattern: Hex reads the name first and crashed on those too.

## 0.1.0

### Added

- Initial version.
