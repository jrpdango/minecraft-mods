# Amphithere Dismount Fix — Project Documentation

Handoff/context document for future sessions working on this repo.

## Overview

A standalone Forge **1.20.1** mod (mod id `amphitherefix`) that patches **Ice and Fire**
(`iceandfire-2.1.13-1.20.1-beta-5.jar`) so that Amphitheres no longer fly up into the sky /
become unreachable when a rider dismounts midair. After the fix they glide down and land nearby.

The patch is implemented as a **Mixin** against `EntityAmphithere`; the Ice and Fire jar is
left untouched.

- MC: `1.20.1`, Forge: `47.4.20`, Java 17, mappings: `official`
- Mod version: `1.0.0`, group: `com.amphitherefix`
- Output jar: `build/libs/amphitherefix-1.0.0.jar`

## Root cause (verified against the shipped jar)

In `com.github.alexthe666.iceandfire.entity.EntityAmphithere`:

```java
// getPositionRelativetoGround(Entity, Level, int, int, RandomSource)
BlockPos pos = new BlockPos(x, entity.getBlockY(), z);
for (int yDown = 0; yDown < 6 + rand.nextInt(6); ++yDown) {
    if (world.isEmptyBlock(pos.below(yDown))) continue;
    return pos.above(yDown);   // <-- BUG: this is ABOVE the current Y, not above the found ground
}
return pos;
```

It scans *downward* for the first solid block (`pos.below(yDown)`, `m_6625_` = `below(int)`)
but then returns `pos.above(yDown)` (`m_6630_` = `above(int)`) — a point ABOVE the Amphithere's
current Y instead of just above the ground it found.

Consequences:

- Near the ground the fly AI targets a point *above* the entity, so it climbs.
- Far from the ground (scan range ≤ 11 blocks) the fallback returns the current position, so a
  flying, riderless Amphithere just hovers at altitude.
- After a midair dismount the Amphithere stays in `isFlying()` mode (nothing forces it down),
  so the wander AI keeps it aloft/unreachable.

### Why dismount matters

Dismount uses `getUntamedRider().isShiftKeyDown()` -> `stopRiding()` (in `tick()`, near the
`dismountIAF()` handling). The client sets a "dismount" bit in the synced `CONTROL_STATE` byte
(bit 3). Nothing clears that byte after the rider leaves, so `dismountIAF()` stays true —
which the fix uses as the "a dismount just happened" signal.

`CONTROL_STATE` bits: bit0 = up, bit1 = down, bit2 = attack, bit3 = dismount
(`setStateField` / `isGoingUp` / `isGoingDown` / `attack` / `dismountIAF`).

## The fix (Mixin)

`src/main/java/com/amphitherefix/mixin/EntityAmphithereMixin.java` — two `@Inject` handlers:

1. `getPositionRelativetoGround` (`HEAD`, cancellable): returns `pos.below(i).above()` (just
   above the ground found below). Fallback returns `pos.below(2)` so a high-altitude dismount
   drifts down instead of hovering.
2. `travel` (`TAIL`): when `dismountIAF() && isFlying() && !onGround() && getPassengers().isEmpty()`,
   apply `setDeltaMovement(y - 0.2)` so a dismounted Amphithere always glides down.

Mixin config: `src/main/resources/amphitherefix.mixins.json` (registered in the mod constructor
via `Mixins.addConfiguration(...)`).

## Key build/remap gotchas (important for future edits)

- **MixinGradle cannot generate refmap entries for non-Minecraft (modded) classes.** Attempting
  `@Inject(method = "getPositionRelativetoGround")` with default remap fails with
  `Unable to locate obfuscation mapping for @Inject target getPositionRelativetoGround`.
  Solution: `remap = false` + exact runtime method names. The generated
  `amphitherefix.refmap.json` is intentionally empty.
- **Name mapping (official dev names vs SRG production names):**
  - Ice and Fire's *own* methods keep their names in the released jar: `dismountIAF`, `isFlying`,
    `setFlying`, `getPositionRelativetoGround`, `getUntamedRider`, `setControlState`, etc.
  - Minecraft overrides are renamed to SRG in the released jar. Relevant ones:
    - `travel(Vec3)` -> `m_7023_` (the Amphithere's flying/movement override)
    - `tick()` -> `m_8119_` (contains the dismount logic)
    - `aiStep()` -> `m_8107_` (flight state logic)
    - `getControllingPassenger()` -> `m_6688_`, `getPassengers()` -> `m_20197_`,
      `getDeltaMovement()` -> `m_20184_`, `setDeltaMovement(Vec3)` -> `m_20256_`,
      `onGround()` -> `m_20096_`, `isEmptyBlock(BlockPos)` -> `m_46859_`,
      `getBlockY()` -> `m_146904_`
  - To bind in BOTH dev (official runtime) and production (SRG runtime) use
    `@Inject(method = {"travel", "m_7023_"}, remap = false)` (first name matches dev, second
    matches the released jar). Plain vanilla method calls in the mixin body need no special
    handling — `reobfJar` renames them to SRG automatically.
- Verified `m_6625_` = `below(int)`, `m_6630_` = `above(int)`, `m_46859_` = `isEmptyBlock`
  (NOT `isEmpty` — that was a compile error) using the obf->srg tsrg
  (`~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/mcp_mappings.tsrg`) and the
  official client mappings.
- `BlockPos.below(int)`/`above(int)` semantics confirmed from the official client.txt mapping
  (`below(int)` -> obfuscated `c`, `above(int)` -> obfuscated `b`).

## Build

```bash
./gradlew jar      # fast: assemble + reobf
./gradlew build    # full lifecycle (also runs check/test; same jar here)
```

Produces `build/libs/amphitherefix-1.0.0.jar`. Drop it into `run/mods/` next to
`iceandfire-2.1.13-1.20.1-beta-5.jar`.

`jar`/`build` are equivalent for this repo — `jar` is `finalizedBy 'reobfJar'`
(build.gradle), so both produce the SRG-reobfuscated production jar.

## Dependencies / build setup

- `build.gradle`:
  - `org.spongepowered.mixin` plugin (0.7.+) + `mixin {}` block (config + refmap output).
  - Run args: `mixin.env.remapRefMap`, `mixin.env.refMapRemappingFile` ->
    `build/createSrgToMcp/output.srg`, `-mixin.config=amphitherefix.mixins.json`.
  - `compileOnly fg.deobf(files('run/mods/iceandfire-2.1.13-1.20.1-beta-5.jar'))`
  - `compileOnly fg.deobf(files('libs/citadel-2.6.3-1.20.1.jar'))` — Citadel is a required
    Ice and Fire dependency and is referenced by `EntityAmphithere`'s class hierarchy, so it
    must be on the compile classpath or compilation fails
    (`class file for IAnimatedEntity not found`).
  - `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`
- `gradle.properties`: `mod_id=amphitherefix`, `mod_name=Amphithere Dismount Fix`,
  `mod_version=1.0.0`, `mod_group_id=com.amphitherefix`.
- `src/main/resources/META-INF/mods.toml` declares mandatory deps on `minecraft`, `forge`,
  and `iceandfire` (version `[2.1.13,)`, ordering AFTER).

## Verification notes (for future sessions)

- The production mixin bytecode was validated by unzipping `build/libs/*.jar` and running
  `javap -p -v` on `EntityAmphithereMixin`: injection method strings are
  `getPositionRelativetoGround` and `{"travel","m_7023_"}` (so `m_7023_` binds at runtime),
  and all vanilla calls are SRG (`m_20096_`, `m_20197_`, `m_20184_`, `m_20334_`, ...).
- `./gradlew runServer` in dev: the fix mod loads, its mixin config is registered/prepared
  (`Preparing amphitherefix.mixins.json (1)`). Full boot is blocked by an **unrelated Citadel**
  dev quirk (`citadel.mixins.json:LevelMixin` fails to shadow `f_46443_` against the
  official-named dev runtime) — does not affect production.
- **Dev runtime caveat:** the Ice and Fire jar in `run/mods/` is SRG-named, so it cannot run
  against the official-named dev runtime. To actually dev-run, put a deobf'd copy on the
  classpath (e.g. `runtimeOnly fg.deobf(files('libs/...jar'))`) and remove the jar from
  `run/mods/` to avoid duplicate-modid issues. Not needed for building the fix jar.
- `run/eula.txt` was temporarily set true then restored to false during testing; `run/` is
  gitignored.

## Files

```
build.gradle
gradle.properties
src/main/resources/amphitherefix.mixins.json
src/main/resources/META-INF/mods.toml
src/main/java/com/amphitherefix/AmphithereFixMod.java
src/main/java/com/amphitherefix/mixin/EntityAmphithereMixin.java
libs/citadel-2.6.3-1.20.1.jar          # compile-only dep (Citadel)
run/mods/iceandfire-2.1.13-1.20.1-beta-5.jar   # the mod being patched (compile-only dep)
```