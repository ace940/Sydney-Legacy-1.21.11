# Sydney Client — 1.21.11 build

Working Fabric 1.21.11 port of Sydney 1.4.7, with Sodium + Lithium, plus a tuned
6b6t config that is loaded by default.

## What is installed

`~/Library/Application Support/minecraft/mods/` contains exactly:

| mod | version |
| --- | --- |
| sydney | 1.4.7-1.21.11 (built from `Sydney-Legacy/`) |
| sodium | 0.8.14+mc1.21.11 |
| lithium | 0.21.4+mc1.21.11 |
| fabric-api | 0.141.6+1.21.11 |

Fabric Loader 0.19.5, Java 21, profile `Fabric 1.21.11` (TLauncher).

Rebuild + install:

```bash
export JAVA_HOME="$HOME/Library/Application Support/minecraft/runtime/java-runtime-delta/osx/java-runtime-delta/jre.bundle/Contents/Home"
cd Sydney-Legacy && ./gradlew build
cp build/libs/sydney-1.4.7-1.21.11.jar "$HOME/Library/Application Support/minecraft/mods/"
```

## Launch

```bash
python3 launch-sydney-1.21.11.py                  # title screen
python3 launch-sydney-1.21.11.py --world "New World"   # straight into a world
python3 launch-sydney-1.21.11.py --dry-run        # print the resolved command
```

Log: `/tmp/sydney-1.21.11-launch.log`

## Port fixes made for 1.21.11

Runtime mixin failures fixed in this pass:

1. `LivingEntityRendererMixin` — `clampBodyYaw` is `static` in 1.21.11; the shadow was
   non-static (mixin apply failure).
2. `LivingEntityRendererMixin` — the whole `updateRenderState` method was `@Overwrite`n, which
   made `fabric-rendering-v1`'s own `@WrapOperation` on `toggleDefaultHeadItem` fail. Replaced with
   a `@ModifyVariable` (static-player tick delta) + a `@Inject(TAIL)` that only overrides
   `bodyYaw` / `relativeHeadYaw` / `pitch` while rotations are being spoofed. Vanilla body is
   preserved, so other mods can still inject.
3. `CameraMixin` — `update(...)` now takes `World` instead of `BlockView`.
4. `AbstractSignBlockEntityRendererMixin` — `renderText` is now
   `(SignBlockEntityRenderState, MatrixStack, OrderedRenderCommandQueue, boolean)`.
5. `SydneyRenderLayers` — `RenderPipeline.Builder.withUniform(name, type, textureFormat)` throws
   for non-texel buffers ("Only texel buffer can specify texture format"); now uses the 2-arg
   overload.
6. Removed the dead `assets/sydney/post_effect/outline.json` + `shaders/program/outline.*`
   post-effect chain: 1.21.9+ changed the JSON schema and nothing referenced it any more (the
   Shaders module uses the vanilla outline pipeline).

Helper scripts:

- `sydney-port-tools/audit_mixins.py` — parses mixin annotations and compares handler
  parameter lists with the mapped 1.21.11 classes (no JDK/`javap` needed).
- `sydney-port-tools/validate_config.py` — checks a config's module/setting keys against
  `@RegisterModule` / `Setting` names in source.

## Config

Active config: `~/Library/Application Support/minecraft/sydney/Configs/6b6t-ultimate.json`
(`General.json` → `"Config": "6b6t-ultimate"`), 88 modules enabled.
An identical copy is kept as `6b6t-ultimate-pristine.json`, because closing the game rewrites
`6b6t-ultimate.json` from live state (`.config load 6b6t-ultimate-pristine` restores it).

Combat

- **AutoCrystal** — Attack + Place, 20 hz, 6.0 / 4.5 wall range, silent switch, packet rotations,
  instant, async, chest break, faceplace `Dynamic` (11 tick delay, health place ≤ 8),
  min damage 5, max self damage 14, anti-suicide, ignore terrain, extrapolation 2, predictions 10.
- **KillAura** — 4.5 / 3.0 wall, 20 hz, Normal rotation, mainhand swing, `Require` switch,
  players/invisibles/shulker bullets, renders target box.
- **Surround** — silent switch, sequential, delay 0, floor + extension, crystal destruction,
  jump/item disable. (This is the "feet place".)
- **AutoTrap** — full head trap, hole check, render.
- **HoleFill** — smart mode, safety, double holes, crystal destruction.
- **MaceAura** — 24 range / 6 attack range, movement assist, extrapolation 1.
- **AutoTotem** — 14 hp, fall distance 15, lethal override, anti-mace, smart mine, gapple slot.
- **AutoArmor** — highest protection, elytra priority, elytra on, smart elytra (spacebar swaps to
  elytra mid-air, swaps back on ground / in webs / while MaceAura is on). This is the chestplate
  swap.
- **SpeedMine** — instant, sequence `Surround` (the "automine"; Sydney has no separate AutoMine
  module), 6.0 range, packet rotations.
- **Velocity** — 0 / 0, explosions + anti-push.
- **Criticals**, **NoHitDelay**, **Reach** 3.2, **KeepSprint**, **NoFall**, **NoSlow**.
- **AutoLog** — enabled, disconnects when you have 0 totems (`Totems` = 1), `SelfDisable` off so it
  stays armed. Note: in a test world with no totems it kicks you immediately.
- **AutoReconnect** is off on purpose — it would loop against AutoLog.

Utility / movement

- Sprint, Step, SafeWalk, ReverseStep, NoJumpDelay, AntiHunger, AntiVoid, AntiLevitation,
  FastPlace, FastWeb, MultiTask, XCarry, Replenish, Blocker, PearlCatcher, AutoLog, AutoRespawn,
  AutoReconnect, AutoBowRelease, AntiPacketKick, NoEntityTrace, TickShift, NoInteract
  (gapple-only so chests still open), RPC, Menu, Commands, ExtraTab, BetterChat, Notifications.

Visuals

- ESP (players/items), Chams (players + crystals, damage tint), NameTags (health, ping, totem
  pops, items, enchants, durability, border), Tracers, ArrowTracers, Trajectories, HoleESP,
  VoidESP, PhaseESP, TextESP, PopChams, BlockHighlight, BreakHighlight, Crosshair, FullBright,
  Atmosphere, HandProgress, ViewModel, Swing, NoRender, NoInterpolation, Icons, Capes,
  DeathEffects, Sounds, custom **Font** (Verdana 18, global, custom shadow).

Binds: AutoCrystal / MaceAura / Reach / AutoTrap are unbuffed (0) on purpose — they stay toggled
on and never fight with Tab / arrow keys.

## Known gaps

- **26.2** (the date-based Minecraft version; `versions/fabric-loader-0.19.5-26.2` exists locally)
  is *not* ported. A 26.2 loom cache and
  `sodium-fabric-0.9.2+mc26.2.jar` exist in the workspace, but Sydney's 1.21.11 renderer code
  targets the 1.21.9+ pipeline API; 26.2 would need another full API pass.
- Sydney has no module literally named `AutoMine` or `FeetPlace` — `SpeedMine` and
  `Surround` (floor) cover them.
- `TableTroll`, `SydneyRobotics`, `ChorusControl` are newer modules missing from this config
  (they load with defaults, disabled).
