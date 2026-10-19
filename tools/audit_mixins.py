#!/usr/bin/env python3
"""Validate sydney mixin targets against the loom-mapped Minecraft jar.

Parses class files directly (no jdk/javap needed) and checks that every
@Mixin target class exists and every referenced target method exists, with
parameter types checked where the annotation makes them unambiguous.

Usage:
    python3 tools/audit_mixins.py        # from anywhere in the checkout
"""
import glob
import os
import re
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java", "me", "aidan", "sydney")

# Annotation types that inject a value / operation as an extra handler parameter,
# so the handler signature is not the target signature plus a CallbackInfo.
VALUE_INJECTORS = {
    "ModifyVariable", "ModifyArg", "ModifyArgs", "ModifyConstant", "ModifyReceiver",
    "ModifyExpressionValue", "ModifyReturnValue", "WrapOperation", "WrapWithCondition",
    "Redirect",
}

# Handler parameters that are mixin/mixinextras plumbing rather than target parameters.
EXTRA = {
    "CallbackInfo", "CallbackInfoReturnable", "Args", "Operation", "LocalRef",
    "Allocation", "Expression", "ExpressionValue", "ParameterList", "Mutable",
    "Type", "Class",
}


def target_version():
    """Minecraft version this checkout builds against (gradle.properties)."""
    props = os.path.join(ROOT, "gradle.properties")
    try:
        with open(props) as fh:
            for line in fh:
                if line.strip().startswith("minecraft_version="):
                    return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return None


def find_jar():
    """Locate the loom-mapped merged jar for this checkout's Minecraft version.

    Path sorting is unreliable here ("1.21.4" sorts after "1.21.11"), so match
    the version from gradle.properties and fall back to the newest file on disk.
    """
    pattern = os.path.expanduser(
        "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/*/"
        "minecraft-merged-*.jar")
    jars = glob.glob(pattern)
    if not jars:
        sys.exit("could not find the loom-mapped minecraft-merged jar; run ./gradlew build first")
    version = target_version()
    if version:
        matching = [j for j in jars
                    if os.path.basename(j).startswith(f"minecraft-merged-{version}-")]
        if matching:
            jars = matching
    return max(jars, key=os.path.getmtime)


JAR = find_jar()
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


def _descriptor_params(desc):
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
    return params


def methods(fqn):
    """All (name, params, access) entries for a class, following nested classes."""
    if fqn in _cache:
        return _cache[fqn]
    candidates = [fqn]
    head, _, tail = fqn.rpartition(".")
    if head:
        candidates.append(f"{head}${tail}")
    parsed, used = [], candidates[0]
    for cand in candidates:
        path = cand.replace(".", "/") + ".class"
        try:
            parsed = _parse(path)
            used = cand
            break
        except KeyError:
            continue
    res = [(name, _descriptor_params(desc), acc) for name, desc, acc in parsed]
    _cache[fqn] = res
    return res


def class_exists(fqn):
    candidates = [fqn]
    head, _, tail = fqn.rpartition(".")
    if head:
        candidates.append(f"{head}${tail}")
    return any(c.replace(".", "/") + ".class" in _zip.namelist() for c in candidates)


def split_params(raw):
    out, depth, cur = [], 0, ""
    for ch in raw:
        if ch in "<([":
            depth += 1
        elif ch in ">)]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip()); cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def clean_type(param):
    """'@Local(ordinal = 1) LocalRef<Style> style' -> ('Style'?, 'LocalRef', annotated).

    Returns (type_name, annotated) where annotated means the parameter carries an
    annotation such as @Local and therefore is not a target parameter.
    """
    annotated = param.lstrip().startswith("@")
    param = re.sub(r"@\w+(?:\s*\((?:[^()]|\([^()]*\))*\))?\s*", "", param, count=1).strip()
    if not param:
        return "", annotated
    tokens = param.rsplit(None, 1)
    raw_type = tokens[0] if len(tokens) > 1 else tokens[0]
    base = re.sub(r"<.*>", "", raw_type).strip()
    arr = ""
    while base.endswith("[]"):
        arr += "[]"
        base = base[:-2].strip()
    base = base.split(".")[-1]
    return base + arr, annotated


def scan_annotations(text, region):
    """Yield (name, args) for annotations in region using balanced parens."""
    out = []
    i = 0
    while i < len(region):
        at = region.find("@", i)
        if at == -1:
            break
        m = re.match(r"@([\w.]+)", region[at:])
        if not m:
            i = at + 1
            continue
        name = m.group(1).split(".")[-1]
        j = at + m.end()
        args = ""
        k = j
        while k < len(region) and region[k] in " \t\r\n":
            k += 1
        if k < len(region) and region[k] == "(":
            depth, start = 0, k
            while k < len(region):
                if region[k] == "(":
                    depth += 1
                elif region[k] == ")":
                    depth -= 1
                    if depth == 0:
                        k += 1
                        break
                k += 1
            args = region[start + 1:k - 1]
        out.append((name, args))
        i = k
    return out


METHOD_RE = re.compile(
    r"(?m)^[ \t]*(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?"
    r"(?:abstract\s+)?(?:synchronized\s+)?[\w<>\[\],.$?\s]+\s+(\w+)\s*\(([^)]*)\)"
    r"\s*(?:throws [\w., ]+)?\{")


def parse_mixins(path):
    text = open(path).read()
    imports = {}
    for m in re.finditer(r"^import\s+(?:static\s+)?([\w.]+)\.(\w+);", text, re.M):
        imports[m.group(2)] = m.group(1) + "." + m.group(2)
    wildcards = re.findall(r"^import\s+([\w.]+)\.\*\s*;", text, re.M)

    def resolve(name):
        head, _, rest = name.partition(".")
        base = imports.get(head)
        if base:
            return base + ("." + rest if rest else "")
        if rest and class_exists(name):
            return name
        for w in wildcards:
            cand = w + "." + head
            if class_exists(cand):
                return cand + ("." + rest if rest else "")
        return name

    target_block = re.search(r"@Mixin\(\{?(.*?)\}?\)\s*(?:public\s+)?(?:abstract\s+)?(?:interface|class)", text, re.S)
    targets = [resolve(x) for x in re.findall(r"([\w.]+)\.class", target_block.group(1))] if target_block else []

    handlers = []
    matches = list(METHOD_RE.finditer(text))
    prev_end = 0
    for m in matches:
        region = text[prev_end:m.start()]
        prev_end = m.end()
        anns = scan_annotations(text, region)
        if not anns:
            continue
        name, args = anns[-1]
        tm = re.search(r'\bmethod\s*=\s*(\{[^}]*\}|"[^"]*")', args, re.S)
        if not tm:
            continue
        raw_target = tm.group(1)
        specs = re.findall(r'"([^"]+)"', raw_target) or [raw_target]
        params = []
        for p in split_params(m.group(2)):
            tname, annotated = clean_type(p)
            if annotated or tname in EXTRA:
                continue
            params.append(tname)
        handlers.append({
            "annotation": name,
            "targets": specs,
            "handler": m.group(1),
            "types": params,
            "decl": m.group(2).strip(),
        })
    return targets, handlers


def describe_class(fqn):
    return fqn.split(".")[-1]


def compatible(handled, params):
    """True when handler params are a type-compatible prefix of the target params."""
    if len(handled) > len(params):
        return False
    for a, b in zip(handled, params):
        if a == b:
            continue
        if re.fullmatch(r"[A-Z]", a):   # unresolved generic type variable (T, K, V...)
            continue
        return False
    return True


def check(path):
    targets, handlers = parse_mixins(path)
    problems = []
    base = os.path.basename(path)
    if not targets:
        return [f"{base}: no @Mixin target found"]

    for target in targets:
        if not class_exists(target):
            problems.append(f"{base}: target class {target} not found in jar")
            continue
        cand = methods(target)
        if not cand:
            continue
        for md in handlers:
            for spec in md["targets"]:
                name = spec.split("(")[0].strip()
                desc = spec[spec.index("("):] if "(" in spec else None
                wildcard = name.endswith("*")
                if wildcard:
                    name = name[:-1]

                if name == "<init>":
                    overloads = [p for (n, p, a) in cand if n == "<init>"]
                elif wildcard:
                    overloads = [p for (n, p, a) in cand if n.startswith(name)]
                else:
                    overloads = [p for (n, p, a) in cand if n == name]

                if not overloads:
                    problems.append(
                        f"{base}: {md['handler']} -> {describe_class(target)}.{spec} missing")
                    continue

                # Only @Inject handlers must mirror the target signature exactly;
                # value injectors add their own value/operation parameter.
                if md["annotation"] != "Inject":
                    continue

                # Mixin lets a handler declare only the leading target parameters it
                # needs (CallbackInfo still has to be last), so compare as a prefix.
                if desc:
                    expected = _descriptor_params(desc)
                    ok = any(compatible(md["types"], p) for p in [expected])
                else:
                    ok = any(compatible(md["types"], p) for p in overloads)

                if not ok:
                    problems.append(
                        f"{base}: {md['handler']} -> {describe_class(target)}.{spec} params "
                        f"{md['types']} match none of {overloads}  [decl: {md['decl']}]")
    return problems


def main():
    files = sorted(glob.glob(os.path.join(SRC, "mixins", "**", "*.java"), recursive=True))
    if not files:
        sys.exit(f"no mixin sources under {SRC}/mixins")
    total = 0
    for f in files:
        for p in check(f):
            print(p)
            total += 1
    print(f"\n{total} issue(s) across {len(files)} mixin files (jar: {os.path.basename(JAR)})")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
