#!/usr/bin/env python3
"""
Launch Sydney Client 1.4.7 on Fabric 1.21.11 with Sodium + Lithium.

Reads the version JSON from the TLauncher Fabric 1.21.11 profile, builds the
classpath, and launches Minecraft with the mods in the default mods folder.

Usage:
    python3 launch-sydney-1.21.11.py            # launch
    python3 launch-sydney-1.21.11.py --dry-run  # print the resolved command
"""
import json, os, subprocess, sys

MC       = os.path.expanduser("~/Library/Application Support/minecraft")
VERSION  = "Fabric 1.21.11"
VJSON    = os.path.join(MC, "versions", VERSION, f"{VERSION}.json")
VJAR     = os.path.join(MC, "versions", VERSION, f"{VERSION}.jar")
NATIVES  = os.path.join(MC, "versions", VERSION, "natives")
ASSETS   = os.path.join(MC, "assets")
LIBS     = os.path.join(MC, "libraries")
JAVA     = os.path.join(MC, "runtime", "java-runtime-delta", "osx",
                        "java-runtime-delta", "jre.bundle", "Contents", "Home", "bin", "java")

# Game directory is the .minecraft folder itself so mods/ is picked up
GAME_DIR = MC

USERNAME = "bro_fr"
UUID     = "614f993286424f348a8af6f92f67c16e"
TOKEN    = "0"
XMS, XMX = "1024m", "4096m"

DROP_FLAGS = {"--demo", "--quickPlayPath", "--quickPlaySingleplayer",
              "--quickPlayMultiplayer", "--quickPlayRealms"}


def rule_allows(rules):
    """Minecraft rule evaluation for macOS with no launcher features."""
    if not rules:
        return True
    allowed = False
    for r in rules:
        os_name = r.get("os", {}).get("name")
        if os_name and os_name not in ("osx", "macos"):
            continue
        if r.get("features"):
            continue
        if r["action"] == "allow":
            allowed = True
        elif r["action"] == "disallow":
            return False
    return allowed


def main():
    dry = "--dry-run" in sys.argv
    if not os.path.exists(JAVA):
        sys.exit(f"java not found: {JAVA}")
    if not os.path.exists(VJSON):
        sys.exit(f"version JSON not found: {VJSON}")

    v = json.load(open(VJSON))

    def maven_path(name):
        parts = name.split(":")
        group, artifact, version = parts[0], parts[1], parts[2]
        classifier = parts[3] if len(parts) > 3 else None
        fn = f"{artifact}-{version}" + (f"-{classifier}" if classifier else "") + ".jar"
        return "/".join(group.split(".") + [artifact, version, fn])

    cp = [VJAR]
    for lib in v["libraries"]:
        if not rule_allows(lib.get("rules")):
            continue
        art = lib.get("artifact")
        if art:
            rel = art["path"]
        else:
            rel = maven_path(lib["name"])
        p = os.path.join(LIBS, rel)
        if not os.path.exists(p):
            sys.exit(f"missing library: {p}")
        cp.append(p)

    subs = {
        "${natives_directory}": NATIVES,
        "${launcher_name}": "tlauncher",
        "${launcher_version}": "1",
        "${classpath}": os.pathsep.join(cp),
        "${classpath_separator}": os.pathsep,
        "${auth_player_name}": USERNAME,
        "${version_name}": VERSION,
        "${game_directory}": GAME_DIR,
        "${assets_root}": ASSETS,
        "${assets_index_name}": v["assetIndex"]["id"],
        "${auth_uuid}": UUID,
        "${auth_access_token}": TOKEN,
        "${auth_session}": f"token:{TOKEN}:{UUID}",
        "${clientid}": "0",
        "${auth_xuid}": "0",
        "${user_type}": "legacy",
        "${version_type}": "release",
        "${resolution_width}": "1280",
        "${resolution_height}": "720",
    }

    def substitute(token):
        for k, val in subs.items():
            if k in token:
                token = token.replace(k, val)
        return token

    def expand(groups):
        out = []
        for grp in groups:
            if not rule_allows(grp.get("rules")):
                continue
            for val in grp["values"]:
                if val in DROP_FLAGS:
                    continue
                t = substitute(val)
                if "${" in t:
                    continue
                out.append(t)
        return out

    args = v["arguments"]
    cmd = [
        JAVA,
        f"-Xms{XMS}",
        f"-Xmx{XMX}",
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:G1NewSizePercent=30",
        "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20",
        "-XX:G1HeapWastePercent=5",
        "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15",
        "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem",
        "-XX:MaxTenuringThreshold=1",
    ]
    cmd += expand(args["jvm"])

    extra_jvm = os.environ.get("SYDNEY_JVM_ARGS", "").strip()
    if extra_jvm:
        cmd += extra_jvm.split()

    cmd += [v["mainClass"]]
    cmd += expand(args["game"])

    # Optional: jump straight into a singleplayer world for testing.
    if "--world" in sys.argv:
        world = sys.argv[sys.argv.index("--world") + 1]
        cmd += ["--quickPlaySingleplayer", world]

    if dry:
        print("java:", JAVA)
        print("classpath entries:", len(cp), "(jar + libs)")
        print("natives:", NATIVES)
        print("gameDir:", GAME_DIR)
        print("assetIndex:", v["assetIndex"]["id"])
        print("mainClass:", v["mainClass"])
        print()
        print("MODS FOLDER:")
        mods = os.path.join(GAME_DIR, "mods")
        if os.path.isdir(mods):
            for f in sorted(os.listdir(mods)):
                if f.endswith(".jar"):
                    print(f"  {f}")
        print()
        print(" ".join(cmd))
        return

    log = "/tmp/sydney-1.21.11-launch.log"
    with open(log, "w") as lf:
        lf.write("CMD: " + " ".join(cmd) + "\n\n")
        lf.flush()
        p = subprocess.Popen(cmd, cwd=GAME_DIR, stdout=lf,
                             stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                             start_new_session=True)
    print(f"🚀 Sydney Client launched!")
    print(f"   PID: {p.pid}")
    print(f"   Version: Fabric 1.21.11")
    print(f"   Config: 6b6t-ultimate")
    print(f"   Log: {log}")
    print(f"   Mods:")
    mods_dir = os.path.join(GAME_DIR, "mods")
    for f in sorted(os.listdir(mods_dir)):
        if f.endswith(".jar"):
            print(f"     • {f}")


main()
