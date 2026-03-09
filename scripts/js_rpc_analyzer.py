#!/usr/bin/env python3
"""
Analyzátor RPC definic z NotebookLM JS bundlu.

Stáhne JS bundle z gstatic a extrahuje:
- RPC registrace (_.Tz definice)
- Request/Response proto třídy
- Kontext kolem každé metody (volající kód)

Použití:
    python js_rpc_analyzer.py                    # Stáhne bundle a analyzuje
    python js_rpc_analyzer.py --bundle /tmp/x.js # Použije existující soubor
    python js_rpc_analyzer.py --method wXbhsf    # Detail jedné metody
"""

import argparse
import json
import os
import re
import sys
from urllib.request import urlopen, Request

# URL hlavního JS bundlu (z HTML stránky notebooklm.google.com)
# Tento URL se mění s každým deployem — pokud nefunguje, stáhni nový z HTML
JS_BUNDLE_URL = (
    "https://www.gstatic.com/_/mss/boq-labs-tailwind/_/js/"
    "k=boq-labs-tailwind.LabsTailwindUi.cs.VP9SH7_wCvQ.es6.O/"
    "d=1/excm=_b/ed=1/dg=0/br=1/wt=2/ujg=1/"
    "rs=ANnj5bGUubJWbaD0Kk0nnQHiUsQNnDCMIw/"
    "ee=Pjplud:PoEs9b;QGR0gd:Mlhmy;ScI3Yc:e7Hzgb;"
    "YIZmRd:A1yn5d;cEt90b:ws9Tlc;dowIGb:ebZ3mb/dti=1/m=_b"
)

CACHE_PATH = "/tmp/notebooklm-js/main_bundle.js"


def download_bundle(url: str = JS_BUNDLE_URL) -> str:
    """Stáhne JS bundle (nebo použije cache)."""
    if os.path.exists(CACHE_PATH) and os.path.getsize(CACHE_PATH) > 100_000:
        print(f"Používám cache: {CACHE_PATH} ({os.path.getsize(CACHE_PATH):,} bytes)")
        with open(CACHE_PATH) as f:
            return f.read()

    print(f"Stahuji JS bundle...")
    req = Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urlopen(req) as resp:
        content = resp.read().decode("utf-8")

    os.makedirs(os.path.dirname(CACHE_PATH), exist_ok=True)
    with open(CACHE_PATH, "w") as f:
        f.write(content)
    print(f"Staženo: {len(content):,} bytes → {CACHE_PATH}")
    return content


def extract_rpc_definitions(js: str) -> list[dict]:
    """Extrahuje všechny _.Tz RPC registrace."""
    # Pattern: new _.Tz("ID", ResponseClass, [_.Mz, bool, _.Oz, "/Service.Method"])
    pattern = r'new _\.Tz\("([^"]+)",(.*?),\s*\[(.*?)\]\)'
    rpcs = []

    for match in re.finditer(pattern, js):
        rpc_id = match.group(1)
        response_class = match.group(2).strip()
        options_str = match.group(3)

        # Extrahuj isGET a service.method
        is_get = "!0" in options_str or ",true," in options_str  # _.Mz,!0 = GET
        method_match = re.search(r'"(/[^"]+)"', options_str)
        method = method_match.group(1) if method_match else "unknown"

        # Zkrátí response class na jméno (pokud je to named class)
        rc_name = response_class
        if "class extends" in rc_name:
            rc_name = "<anonymous proto>"
        elif len(rc_name) < 30:
            rc_name = rc_name.strip()

        rpcs.append({
            "id": rpc_id,
            "method": method,
            "is_get": is_get,
            "response_class": rc_name,
            "raw_options": options_str[:100],
            "position": match.start(),
        })

    return rpcs


def extract_method_context(js: str, rpc_id: str, window: int = 500) -> str:
    """Extrahuje kontext kolem RPC ID v JS bundlu."""
    idx = js.find(f'"{rpc_id}"')
    if idx == -1:
        return f"RPC ID '{rpc_id}' nenalezeno v bundlu"

    start = max(0, idx - window)
    end = min(len(js), idx + window)
    context = js[start:end]

    # Zvýrazni RPC ID
    context = context.replace(f'"{rpc_id}"', f'\n>>> "{rpc_id}" <<<\n')
    return context


def find_callers(js: str, rpc_id: str) -> list[str]:
    """Hledá místa kde se RPC ID volá (ne jen registruje)."""
    callers = []
    # Hledej variable name přiřazené k Tz registraci
    # Pattern: var XXX = new _.Tz("rpc_id", ...)
    var_pattern = rf'(?:var |const |let )(\w+)\s*=\s*new _\.Tz\("{rpc_id}"'
    var_match = re.search(var_pattern, js)
    var_name = var_match.group(1) if var_match else None

    if var_name:
        # Najdi použití této proměnné
        usage_pattern = rf'\b{re.escape(var_name)}\b'
        for match in re.finditer(usage_pattern, js):
            pos = match.start()
            # Kontext ±100 znaků
            start = max(0, pos - 80)
            end = min(len(js), pos + 80)
            line = js[start:end].replace('\n', ' ')
            callers.append(line)

    return callers


def extract_request_structure(js: str, rpc_id: str) -> dict:
    """Pokouší se extrahovat request strukturu z okolního kódu."""
    # Hledá buildRequest / encode pattern kolem RPC ID
    idx = js.find(f'"{rpc_id}"')
    if idx == -1:
        return {}

    # Podívej se na 2000 znaků před a po
    region = js[max(0, idx - 2000):idx + 2000]

    # Hledej proto field přístupy (_.Fd, _.Us, _.Qp — getter funkce)
    getters = re.findall(r'_\.(Fd|Us|Qp|Wm|Cy)\((?:this|[a-z]),\s*(\d+)', region)

    # Hledej proto field settery
    setters = re.findall(r'_\.(Bh|Ge|jf)\((?:this|[a-z]),\s*(\d+)', region)

    return {
        "field_getters": [(fn, int(idx)) for fn, idx in getters],
        "field_setters": [(fn, int(idx)) for fn, idx in setters],
    }


def analyze_all(js: str, verbose: bool = False):
    """Hlavní analýza celého bundlu."""
    rpcs = extract_rpc_definitions(js)
    print(f"\nNalezeno {len(rpcs)} RPC registrací\n")

    # Seskup podle service
    by_service = {}
    for rpc in rpcs:
        svc = rpc["method"].split(".")[0].lstrip("/") if "." in rpc["method"] else "unknown"
        by_service.setdefault(svc, []).append(rpc)

    for svc, methods in sorted(by_service.items()):
        print(f"\n{'='*60}")
        print(f"  Service: {svc}")
        print(f"{'='*60}")
        print(f"{'RPC ID':<12} {'GET/POST':<10} {'Metoda'}")
        print("-" * 60)
        for rpc in sorted(methods, key=lambda x: x["method"]):
            t = "GET" if rpc["is_get"] else "POST"
            method_short = rpc["method"].split(".")[-1] if "." in rpc["method"] else rpc["method"]
            print(f"{rpc['id']:<12} {t:<10} {method_short}")

            if verbose:
                structure = extract_request_structure(js, rpc["id"])
                if structure.get("field_getters"):
                    fields = sorted(set(structure["field_getters"]), key=lambda x: x[1])
                    field_str = ", ".join(f"field_{idx}({fn})" for fn, idx in fields)
                    print(f"{'':>12} Fields: {field_str}")

    return rpcs


def analyze_method(js: str, rpc_id: str):
    """Detailní analýza jedné metody."""
    rpcs = extract_rpc_definitions(js)
    rpc = next((r for r in rpcs if r["id"] == rpc_id), None)

    if not rpc:
        print(f"RPC ID '{rpc_id}' nenalezeno")
        return

    print(f"\n{'='*60}")
    print(f"RPC ID:          {rpc['id']}")
    print(f"gRPC metoda:     {rpc['method']}")
    print(f"Typ:             {'GET' if rpc['is_get'] else 'POST'}")
    print(f"Response class:  {rpc['response_class']}")
    print(f"Pozice v bundlu: {rpc['position']}")

    # Kontext
    print(f"\n--- Kontext (±500 znaků) ---")
    context = extract_method_context(js, rpc_id, 500)
    print(context)

    # Request struktura
    print(f"\n--- Request fields ---")
    structure = extract_request_structure(js, rpc_id)
    if structure.get("field_getters"):
        for fn, idx in sorted(set(structure["field_getters"]), key=lambda x: x[1]):
            print(f"  field {idx}: {fn}()")
    else:
        print("  (nepodařilo se extrahovat)")

    # Volající
    print(f"\n--- Volající kód ---")
    callers = find_callers(js, rpc_id)
    if callers:
        for c in callers[:10]:
            print(f"  {c[:120]}")
    else:
        print("  (nenalezeno)")


def main():
    parser = argparse.ArgumentParser(description="NotebookLM JS bundle RPC analyzátor")
    parser.add_argument("--bundle", help="Cesta k JS bundlu (jinak stáhne z gstatic)")
    parser.add_argument("--method", "-m", help="Detail jedné metody (RPC ID)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Detailní výpis polí")
    parser.add_argument("--json", action="store_true", help="JSON výstup")
    args = parser.parse_args()

    if args.bundle:
        with open(args.bundle) as f:
            js = f.read()
    else:
        js = download_bundle()

    if args.method:
        analyze_method(js, args.method)
    elif args.json:
        rpcs = extract_rpc_definitions(js)
        print(json.dumps(rpcs, indent=2, ensure_ascii=False))
    else:
        analyze_all(js, args.verbose)


if __name__ == "__main__":
    main()
