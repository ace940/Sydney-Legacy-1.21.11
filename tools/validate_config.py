#!/usr/bin/env python3
"""Validate a Sydney config against the module/setting names declared in source."""
import json, os, re, sys, glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java", "me", "aidan", "sydney", "modules", "impl")
CFG = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/Library/Application Support/minecraft/Sydney/Configs/6b6t-ultimate.json")

# Every module inherits these three settings from Module's constructor.
BASE_SETTINGS = {"ChatNotify", "Drawn", "Bind"}

modules = {}   # name -> set(settings)
for path in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
    text = open(path).read()
    m = re.search(r'@RegisterModule\([^)]*name\s*=\s*"([^"]+)"', text, re.S)
    if not m:
        continue
    name = m.group(1)
    settings = set(re.findall(r'new\s+\w*Setting\(\s*"([^"]+)"', text)) | BASE_SETTINGS
    modules[name] = settings

cfg = json.load(open(CFG))
unknown_mod, unknown_set, missing = [], [], []
enabled = []
for name, data in cfg.get("Modules", {}).items():
    if name not in modules:
        unknown_mod.append(name); continue
    if data.get("Status"):
        enabled.append(name)
    for key in data.get("Settings", {}):
        if key not in modules[name]:
            unknown_set.append(f"{name}.{key}")

for name in modules:
    if name not in cfg.get("Modules", {}):
        missing.append(name)

print(f"config: {CFG}")
print(f"modules in source: {len(modules)}, in config: {len(cfg.get('Modules', {}))}, enabled: {len(enabled)}")
if unknown_mod: print("UNKNOWN MODULES:", unknown_mod)
if unknown_set: print("UNKNOWN SETTINGS:", unknown_set)
if missing: print("MISSING MODULES (defaults used):", missing)
print("ENABLED:", ", ".join(sorted(enabled)))

sys.exit(1 if (unknown_mod or unknown_set) else 0)
