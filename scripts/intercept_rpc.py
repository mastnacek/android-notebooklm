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

        # 3. V detailu sešitu — hledej prompt suggestions
        print("[3] Hledám prompt suggestions v chatu...")
        c0 = len(captured)
        # Počkej na chat input a klikni do něj
        chat_input = page.locator('textarea, input[type="text"], [contenteditable="true"], .chat-input')
        if chat_input.count() > 0:
            chat_input.first.click()
            time.sleep(3)
        else:
            # Zkus najít jakýkoli input element
            page.evaluate("""() => {
                const inputs = document.querySelectorAll('input, textarea, [contenteditable]');
                if (inputs.length > 0) inputs[inputs.length - 1].focus();
            }""")
            time.sleep(3)

        # Najdi chip/suggestion elementy
        chips = page.evaluate("""() => {
            const sels = [
                '.suggestion-chip', '.prompt-suggestion', '[class*="suggestion"]',
                '[class*="chip"]', '.mat-chip', 'mat-chip',
                '[class*="prompt"]', '.quick-prompt'
            ];
            const found = [];
            for (const sel of sels) {
                const els = document.querySelectorAll(sel);
                for (const el of els) {
                    found.push({sel, text: el.textContent.trim().substring(0, 100), cls: el.className.substring(0, 80)});
                }
            }
            return found;
        }""")
        print(f"    Chipy: {json.dumps(chips, ensure_ascii=False)[:500]}")
        page.screenshot(path=str(CAPTURES_DIR / "chat_area.png"))

        # Zkus hledat RPCy pro suggestions
        new_rpcs = [c for c in captured[c0:] if c['rpc_id'] == 'otmP3b']
        if new_rpcs:
            print(f"    GeneratePromptSuggestions zachycen! params={json.dumps(new_rpcs[0]['params'], ensure_ascii=False)[:300]}")
        else:
            print(f"    +{len(captured) - c0} RPC (žádný otmP3b)")

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
