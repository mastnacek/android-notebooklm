# /// script
# requires-python = ">=3.11"
# dependencies = ["patchright"]
# ///
"""Zachytí batchexecute RPC z NotebookLM."""

import json
import time
from pathlib import Path
from urllib.parse import unquote, urlparse, parse_qs

from patchright.sync_api import sync_playwright

SKILL_PROFILE = Path.home() / ".claude/skills/notebooklm/data/browser_profile"
CAPTURES_DIR = Path(__file__).parent / "rpc_captures"
CAPTURES_DIR.mkdir(exist_ok=True)

captured = []


def on_request(request):
    url = request.url
    if "batchexecute" not in url:
        return

    qs = parse_qs(urlparse(url).query)
    source_path = qs.get("source-path", ["/"])[0]
    rpc_ids = qs.get("rpcids", ["?"])[0]
    post = request.post_data or ""

    # Parsuj f.req z POST body
    params = None
    if post:
        try:
            post_qs = parse_qs(post)
            freq_list = post_qs.get("f.req", [])
            if freq_list:
                data = json.loads(freq_list[0])
                # Struktura: [[[rpc_id, params_json, null, "generic"]]]
                for outer in data:
                    if not isinstance(outer, list):
                        continue
                    for inner in outer:
                        if not isinstance(inner, list) or len(inner) < 2:
                            continue
                        if isinstance(inner[0], str):
                            # inner = [rpc_id, params_json, ...]
                            params_raw = inner[1]
                            params = json.loads(params_raw) if isinstance(params_raw, str) else params_raw
                            break
            else:
                # Debug: co je v POST
                keys = list(post_qs.keys())
                print(f"    [debug] POST keys: {keys}, post[:200]: {post[:200]}")
        except Exception as e:
            print(f"    [debug parse error] {e}, post[:200]: {post[:200]}")

    entry = {
        "rpc_id": rpc_ids,
        "method": request.method,
        "params": params,
        "source_path": source_path,
        "post_len": len(post),
    }
    captured.append(entry)
    p_str = json.dumps(params, ensure_ascii=False)[:300] if params else "null"
    print(f"  {request.method:4} {rpc_ids:10} path={source_path[:50]}  post={len(post)}  params={p_str}")


def main():
    for f in ["SingletonLock", "SingletonSocket", "SingletonCookie"]:
        (SKILL_PROFILE / f).unlink(missing_ok=True)

    NB_ID = "d07d4801-9d46-4de0-851a-d6a0d4af1318"

    with sync_playwright() as p:
        browser = p.chromium.launch_persistent_context(
            str(SKILL_PROFILE), headless=True,
            viewport={"width": 1280, "height": 900},
        )
        page = browser.pages[0] if browser.pages else browser.new_page()
        page.on("request", on_request)

        # 1. Hlavní stránka
        print("\n[1] Načítám NotebookLM...")
        page.goto("https://notebooklm.google.com/")
        time.sleep(8)
        print(f"    → {len(captured)} RPC\n")

        # 2. Detail sešitu
        print("[2] Otevírám sešit...")
        c0 = len(captured)
        page.goto(f"https://notebooklm.google.com/notebook/{NB_ID}")
        time.sleep(8)
        print(f"    → +{len(captured) - c0} RPC\n")

        # 3. Zpět na seznam, otevři ⋮ menu na kartě
        print("[3] Zpět na seznam, hledám menu karty...")
        page.goto("https://notebooklm.google.com/")
        time.sleep(5)

        # ⋮ tlačítka na kartách — v seznamu sešitů
        page.screenshot(path=str(CAPTURES_DIR / "list.png"))
        # Hover nad kartou aby se ukázal ⋮
        cards = page.locator('button.primary-action-button')
        if cards.count() > 0:
            cards.first.hover()
            time.sleep(1)
            # Teď klikni na ⋮ (secondary-action-button nebo mat-icon more_vert v blízkosti)
            dots = page.locator('button.secondary-action-button').first
            if dots.is_visible():
                dots.click(force=True)
                time.sleep(2)
                page.screenshot(path=str(CAPTURES_DIR / "card_menu.png"))
                items = page.locator('[role="menuitem"], button.mat-mdc-menu-item')
                print("    Menu položky:")
                for i in range(items.count()):
                    txt = items.nth(i).text_content()
                    if txt:
                        print(f"      [{i}] {txt.strip()}")
                page.keyboard.press("Escape")
            else:
                print("    ⋮ není viditelné po hoveru")
        else:
            print("    Žádné karty")

        # Uložit
        print(f"\n{'='*60}")
        print(f"  Celkem: {len(captured)} RPC")
        print(f"{'='*60}")
        out = CAPTURES_DIR / "captures.json"
        out.write_text(json.dumps(captured, indent=2, ensure_ascii=False))
        print(f"  → {out}")
        browser.close()


if __name__ == "__main__":
    main()
