#!/usr/bin/env python3
"""Validate sydney mixin handler signatures against the mapped Minecraft jar.

Parses class files directly (no jdk/javap needed) and flags handlers whose
parameter count matches a target overload but whose types differ, plus
annotations pointing at methods that do not exist.
"""
import io, os, re, struct, zipfile, glob, sys

JAR = os.path.expanduser(
    "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/"
    "1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/"
    "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar")
SRC = "Sydney-Legacy/src/main/java/me/aidan/sydney"

EXTRA = {"CallbackInfo", "CallbackInfoReturnable", "Args", "Operation", "LocalRef",
         "Allocation", "Expression", "ExpressionValue", "ParameterList", "Mutable"}

_zip = zipfile.ZipFile(JAR)
_cache = {}


def _parse(path):
    data = _zip.read(path)
    pos = 10                      # magic, minor, major, cp_count
    cp_count = struct.unpack_from(">H", data, 8)[0]
    cp = [None] * cp_count
    i = 1
    while i < cp_count:
        tag = data[pos]; pos += 1
        if tag == 1:
            ln = struct.unpack_from(">H", data, pos)[0]; pos += 2
            cp[i] = data[pos:pos + ln].decode("utf-8", "replace"); pos += ln
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = ("ref", struct.unpack_from(">H", data, pos)[0]); pos += 2
        elif tag == 15:
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8; i += 1
        else:
            raise ValueError(f"bad cp tag {tag}")
        i += 1
    pos += 6                      # access flags, this, super
    iface_count = struct.unpack_from(">H", data, pos)[0]; pos += 2 + 2 * iface_count
    field_count = struct.unpack_from(">H", data, pos)[0]; pos += 2
    for _ in range(field_count):
        pos += 6
        ac = struct.unpack_from(">H", data, pos)[0]; pos += 2
        for _ in range(ac):
            pos += 2
            ln = struct.unpack_from(">I", data, pos)[0]; pos += 4 + ln
    meth_count = struct.unpack_from(">H", data, pos)[0]; pos += 2
    out = []
    for _ in range(meth_count):
        acc, name_i, desc_i = struct.unpack_from(">HHH", data, pos); pos += 6
        ac = struct.unpack_from(">H", data, pos)[0]; pos += 2
        for _ in range(ac):
            pos += 2
            ln = struct.unpack_from(">I", data, pos)[0]; pos += 4 + ln
        out.append((cp[name_i], cp[desc_i], acc))
    return out


def methods(fqn):
    if fqn in _cache:
        return _cache[fqn]
    path = fqn.replace(".", "/") + ".class"
    try:
        parsed = _parse(path)
    except KeyError:
        parsed = []
    res = []
    for name, desc, acc in parsed:
        if name.startswith("<"):
            continue
        args = desc[1:desc.index(")")]
        params, i = [], 0
        while i < len(args):
            arr = 0
            while args[i] == "[":
                arr += 1; i += 1
            if args[i] == "L":
                end = args.index(";", i)
                t = args[i + 1:end].split("/")[-1]
                i = end + 1
            else:
                t = {"Z": "boolean", "B": "byte", "C": "char", "S": "short",
                     "I": "int", "J": "long", "F": "float", "D": "double"}[args[i]]
                i += 1
            params.append(t + "[]" * arr)
        res.append((name, params, acc))
    _cache[fqn] = res
    return res


def split_params(raw):
    out, depth, cur = [], 0, ""
    for ch in raw:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip()); cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def collect(path):
    text = open(path).read()
    imports = {}
    for m in re.finditer(r"^import\s+([\w.]+)\.(\w+);", text, re.M):
        imports[m.group(2)] = m.group(1) + "." + m.group(2)

    def resolve(t):
        base = re.sub(r"<.*>", "", t).strip()
        arr = base.endswith("[]")
        base = base.rstrip("[]")
        return base + ("[]" if arr else "")

    m = re.search(r"@Mixin\(\{?(.*?)\}?\)\s*(?:public\s+)?(?:abstract\s+)?(?:interface|class)", text, re.S)
    targets = []
    if m:
        for x in re.findall(r"([\w.]+)\.class", m.group(1)):
            targets.append(imports.get(x.split(".")[-1], x))

    methods_out = []
    for am in re.finditer(r"@(Inject|ModifyVariable|ModifyArg|ModifyArgs|ModifyConstant|Redirect|WrapOperation|WrapWithCondition|ModifyExpressionValue)\((.*?)\)\s*(?=@|private|public|protected)", text, re.S):
        tm = re.search(r'method\s*=\s*"([^"]+)"', am.group(2))
        if not tm:
            continue
        rest = text[am.end():]
        dm = re.search(r"\b\w[\w<>\[\], .]*\s+(\w+)\s*\((.*?)\)\s*(?:throws [\w., ]+)?\s*\{", rest, re.S)
        if not dm:
            continue
        params = split_params(dm.group(2))
        methods_out.append({
            "annotation": am.group(1),
            "target": tm.group(1),
            "handler": dm.group(1),
            "types": [resolve(p.split()[-1] if len(p.split()) > 1 else p) for p in params],
            "raw": dm.group(2),
        })
    return targets, methods_out


def check(path):
    targets, handlers = collect(path)
    problems = []
    base = os.path.basename(path)
    for t in targets:
        cand = methods(t)
        if not cand:
            problems.append(f"{base}: target class {t} not found in jar")
            continue
        byname = {}
        for name, params, acc in cand:
            byname.setdefault(name, []).append(params)
        for md in handlers:
            tname = md["target"].split("(")[0]
            if tname not in byname:
                problems.append(f"{base}: {md['annotation']} {md['handler']} -> {tname!r} missing in {t.split('.')[-1]}")
                continue
            handed = [p for p in md["types"] if p.split("<")[0] not in EXTRA]
            flagged = False
            for overload in byname[tname]:
                if len(overload) != len(handed):
                    continue
                bad = [(a, b) for a, b in zip(handed, overload) if a != b]
                if bad:
                    problems.append(f"{base}: {md['handler']} -> {t.split('.')[-1]}.{tname} mismatch {bad}  [decl: {md['raw']}]")
                    flagged = True
                    break
            _ = flagged
    return problems


def main():
    files = sorted(glob.glob(SRC + "/mixins/**/*.java", recursive=True))
    total = 0
    for f in files:
        for p in check(f):
            print(p); total += 1
    print(f"\n{total} issue(s) across {len(files)} mixin files")


if __name__ == "__main__":
    main()
