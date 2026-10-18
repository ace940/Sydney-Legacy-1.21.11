<div align="center">
<img src="https://i.imgur.com/cKfB84P.png" width="90%" />

<img src="https://i.imgur.com/rmIs4Hw.png" width="90%" />
</div>

# Sydney-Legacy — Minecraft 1.21.11

Sydney is a Minecraft utility mod aimed at anarchy servers. This branch is a working port of
Sydney 1.4.7 to **Fabric 1.21.11** (Java 21, Loader 0.19.5), tested with **Sodium 0.8.14** and
**Lithium 0.21.4**.

Detailed notes, config reference and known gaps: [docs/PORT-1.21.11.md](docs/PORT-1.21.11.md).

## Build

```bash
./gradlew build
# -> build/libs/sydney-1.4.7-1.21.11.jar
```

Install the jar in `.minecraft/mods` together with Fabric API, Sodium and Lithium for 1.21.11.

## Running

`scripts/launch-sydney-1.21.11.py` builds the classpath from an installed Fabric 1.21.11 profile
and starts the game (macOS/TLauncher paths, edit the constants at the top for your setup):

```bash
python3 scripts/launch-sydney-1.21.11.py                 # title screen
python3 scripts/launch-sydney-1.21.11.py --world "New World"   # straight into a world
python3 scripts/launch-sydney-1.21.11.py --dry-run        # print the resolved JVM command
SYDNEY_JVM_ARGS="-Dfoo=bar" python3 scripts/launch-sydney-1.21.11.py   # extra JVM args
```

## Config

`configs/6b6t-ultimate.json` is a fully tuned 6b6t profile (88 modules: AutoCrystal, KillAura,
Surround floor/feet placement, AutoTrap, HoleFill, MaceAura, AutoTotem, AutoArmor chestplate and
elytra swapping, SpeedMine, Velocity, Reach, plus the full ESP/Chams/NameTags/Tracers/HoleESP
visual stack and a custom font).

Copy it to `~/Library/Application Support/minecraft/sydney/Configs/6b6t-ultimate.json`
(or `%appdata%\.minecraft\sydney\Configs\`) and set `"Config": "6b6t-ultimate"` in `General.json`.

## Tools

| Tool | Purpose |
| --- | --- |
| `tools/audit_mixins.py` | Verifies every mixin handler's parameter list against the mapped Minecraft classes (pure Python, no JDK needed). |
| `tools/validate_config.py` | Checks a config file's module/setting keys against `@RegisterModule` and `Setting` names in the source. |
| `tools/frame_metrics.py` | Dependency-free PNG reader used to detect visual breakage frame-by-frame during a module stress run. |
| `src/main/java/me/aidan/sydney/debug/StressHarness.java` | Dev harness (`-Dsydney.stressTest=<dir>`) that toggles every module inside a loaded world and records per-module results. |

## License

GPL-3.0, see [LICENSE](LICENSE).
