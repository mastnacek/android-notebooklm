#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = ["requests", "patchright"]
# ///
"""
Kompletní testovací pipeline pro NotebookLM RPC metody.

Automaticky:
1. Spustí Playwright s uloženým browser profilem
2. Naviguje na notebooklm.google.com a extrahuje cookies + tokeny
3. Získá seznam notebooků (ověření auth)
4. Otestuje všechny bezpečné metody (GET + vybrané POST)
5. Generuje report

Použití:
    uv run rpc_pipeline.py                  # Kompletní test
    uv run rpc_pipeline.py --get-only       # Jen GET metody
    uv run rpc_pipeline.py --reauth         # Otevře browser pro nový login
    uv run rpc_pipeline.py --report out.json # Ulož report do souboru
"""

import argparse
import asyncio
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path

# Přidej scripts/ do path pro import rpc_tester
sys.path.insert(0, str(Path(__file__).parent))
from rpc_tester import BatchExecuteClient, RPC_CATALOG, get_test_params

BROWSER_PROFILE = os.path.expanduser("~/.claude/skills/notebooklm/data/browser_profile")
COOKIES_CACHE = os.path.expanduser("~/.notebooklm-cookies.json")


# ── Fáze 1: Získání cookies přes Playwright ──

async def extract_cookies_from_browser(headless: bool = True) -> dict | None:
    """Spustí Playwright s persistent profilem, naviguje na NotebookLM,
    extrahuje cookies + CSRF token + session ID."""
    from patchright.async_api import async_playwright

    print("── Fáze 1: Extrakce cookies z browseru ──")

    if not os.path.exists(BROWSER_PROFILE):
        print(f"  CHYBA: Browser profil neexistuje: {BROWSER_PROFILE}")
        print(f"  Spusť nejdřív: python ~/.claude/skills/notebooklm/scripts/run.py auth_manager.py setup")
        return None

    print(f"  Profil: {BROWSER_PROFILE}")
    print(f"  Headless: {headless}")

    async with async_playwright() as p:
        context = await p.chromium.launch_persistent_context(
            BROWSER_PROFILE,
            headless=headless,
            args=["--disable-blink-features=AutomationControlled"],
        )
        page = context.pages[0] if context.pages else await context.new_page()

        print("  Naviguju na notebooklm.google.com...")
        try:
            await page.goto("https://notebooklm.google.com/", wait_until="domcontentloaded", timeout=30000)
            await page.wait_for_timeout(5000)
        except Exception as e:
            print(f"  CHYBA navigace: {e}")
            await context.close()
            return None

        # Zkontroluj jestli jsme přihlášení (ne na login stránce)
        url = page.url
        print(f"  URL: {url}")
        if "accounts.google.com" in url or "signin" in url.lower():
            if headless:
                print("  CHYBA: Nepřihlášen! Spusť s --reauth pro manuální login.")
                await context.close()
                return None
            else:
                print("  Čekám na přihlášení... (přihlaš se v browseru)")
                try:
                    await page.wait_for_url("**/notebooklm.google.com/**", timeout=120000)
                    await page.wait_for_timeout(5000)
                except Exception:
                    print("  CHYBA: Timeout čekání na přihlášení")
                    await context.close()
                    return None

        # Extrahuj cookies
        browser_cookies = await context.cookies("https://notebooklm.google.com")
        cookie_str = "; ".join(f"{c['name']}={c['value']}" for c in browser_cookies)
        print(f"  Cookies: {len(browser_cookies)} kusů ({len(cookie_str)} chars)")

        # Extrahuj CSRF token a session ID z HTML
        html = await page.content()
        csrf_match = re.search(r'"SNlM0e"\s*:\s*"([^"]+)"', html)
        sid_match = re.search(r'"FdrFJe"\s*:\s*"([^"]+)"', html)

        if not csrf_match or not sid_match:
            print("  CHYBA: CSRF token nebo session ID nenalezeny v HTML")
            # Debug: vypiš klíče z WIZ_global_data
            keys = re.findall(r'"(\w+)"\s*:', html[:5000])
            print(f"  HTML klíče: {keys[:20]}")
            await context.close()
            return None

        csrf_token = csrf_match.group(1)
        session_id = sid_match.group(1)
        print(f"  CSRF: {csrf_token[:20]}...")
        print(f"  SID:  {session_id}")

        # Najdi první notebook ID pro testování
        notebook_id = None
        nb_match = re.search(r'/notebook/([a-f0-9-]{36})', html)
        if nb_match:
            notebook_id = nb_match.group(1)
            print(f"  Notebook: {notebook_id}")

        await context.close()

        result = {
            "cookies": cookie_str,
            "csrf_token": csrf_token,
            "session_id": session_id,
            "notebook_id": notebook_id,
            "saved_at": datetime.now().isoformat(),
        }

        # Cache na disk
        with open(COOKIES_CACHE, "w") as f:
            json.dump(result, f, indent=2)
        os.chmod(COOKIES_CACHE, 0o600)
        print(f"  Uloženo do {COOKIES_CACHE}")

        return result


# ── Fáze 2: Test auth ──

def test_auth(client: BatchExecuteClient) -> tuple[bool, list[dict] | None]:
    """Otestuje auth voláním list_notebooks. Vrátí (ok, notebooks)."""
    print("\n── Fáze 2: Ověření auth (list_notebooks) ──")
    result = client.call("wXbhsf", [None, 1, None, [2]])

    if result["error"]:
        print(f"  CHYBA: {result['error']}")
        print(f"  Raw: {result['raw'][:300]}")
        return False, None

    notebooks = []
    try:
        data = result["parsed"]
        if data and data[0]:
            for item in data[0]:
                title = item[0] if item[0] else "Untitled"
                nb_id = item[2] if len(item) > 2 else "?"
                emoji = item[3] if len(item) > 3 else ""
                notebooks.append({"id": nb_id, "title": title, "emoji": emoji})
    except (IndexError, TypeError):
        pass

    print(f"  OK — {len(notebooks)} notebooků nalezeno")
    for nb in notebooks[:5]:
        print(f"    {nb['emoji']} {nb['title'][:40]}  [{nb['id'][:8]}...]")
    if len(notebooks) > 5:
        print(f"    ... a dalších {len(notebooks) - 5}")

    return True, notebooks


# ── Fáze 3: Testování metod ──

# Metody bezpečné pro testování (nemění data)
SAFE_GET_METHODS = [
    "list_notebooks", "list_featured", "list_models", "get_account",
    "access_token", "artifact_customization",
]

# GET metody vyžadující notebook_id
NOTEBOOK_GET_METHODS = [
    "get_notebook", "list_artifacts", "get_notes", "notebook_guide",
    "list_chat_sessions", "prompt_suggestions", "doc_guides",
    "report_suggestions", "check_freshness", "act_on_sources",
    "magic_index",
]

# POST metody bezpečné pro testování (read-only efekt)
SAFE_POST_METHODS = [
    "get_account",  # GetOrCreateAccount — jen čte existující
]


def test_methods(
    client: BatchExecuteClient,
    notebook_id: str | None,
    get_only: bool = False,
) -> list[dict]:
    """Otestuje RPC metody. Vrátí seznam výsledků."""
    print("\n── Fáze 3: Testování RPC metod ──")

    results = []

    # Bezpečné GET metody (bez notebook_id)
    print("\n  --- GET metody (globální) ---")
    for name in SAFE_GET_METHODS:
        result = _test_one(client, name, None)
        results.append(result)
        time.sleep(0.3)

    # GET metody s notebook_id
    if notebook_id:
        print(f"\n  --- GET metody (notebook: {notebook_id[:8]}...) ---")
        for name in NOTEBOOK_GET_METHODS:
            result = _test_one(client, name, notebook_id)
            results.append(result)
            time.sleep(0.3)
    else:
        print("\n  PŘESKOČENO: GET metody s notebook_id (žádný notebook nenalezen)")
        for name in NOTEBOOK_GET_METHODS:
            results.append({"method": name, "status": "SKIP", "reason": "no notebook_id"})

    # POST metody (jen pokud --get-only není nastaveno)
    if not get_only:
        print(f"\n  --- POST metody (bezpečné) ---")
        for name in SAFE_POST_METHODS:
            result = _test_one(client, name, notebook_id)
            results.append(result)
            time.sleep(0.3)

    return results


def _test_one(client: BatchExecuteClient, method_name: str, notebook_id: str | None) -> dict:
    """Otestuje jednu metodu."""
    info = RPC_CATALOG.get(method_name)
    if not info:
        return {"method": method_name, "status": "ERROR", "reason": "unknown method"}

    params = get_test_params(method_name, notebook_id)
    if params is None:
        print(f"    SKIP  {method_name:<30} (potřebuje notebook_id)")
        return {"method": method_name, "status": "SKIP", "reason": "needs notebook_id"}

    source_path = f"/notebook/{notebook_id}" if notebook_id else "/"

    start = time.time()
    result = client.call(info["id"], params, source_path)
    elapsed = time.time() - start

    if result["error"]:
        status = "FAIL"
        detail = result["error"][:100]
        print(f"    FAIL  {method_name:<30} ({elapsed:.1f}s) — {detail}")
    elif result["parsed"] is None:
        status = "EMPTY"
        detail = "null response"
        print(f"    EMPTY {method_name:<30} ({elapsed:.1f}s)")
    else:
        status = "OK"
        parsed = result["parsed"]
        size = len(json.dumps(parsed, default=str))
        detail = f"{size} bytes"
        print(f"    OK    {method_name:<30} ({elapsed:.1f}s) — {size:,} bytes")

    return {
        "method": method_name,
        "rpc_id": info["id"],
        "grpc_method": info["method"],
        "status": status,
        "detail": detail,
        "elapsed_s": round(elapsed, 2),
        "response_preview": json.dumps(result["parsed"], ensure_ascii=False, default=str)[:500]
            if result["parsed"] else None,
    }


# ── Fáze 4: Report ──

def print_report(results: list[dict], report_path: str | None = None):
    """Vypíše a volitelně uloží report."""
    print("\n" + "=" * 60)
    print("  REPORT")
    print("=" * 60)

    ok = [r for r in results if r["status"] == "OK"]
    fail = [r for r in results if r["status"] == "FAIL"]
    empty = [r for r in results if r["status"] == "EMPTY"]
    skip = [r for r in results if r["status"] == "SKIP"]

    print(f"\n  ✓ OK:    {len(ok)}")
    print(f"  ✗ FAIL:  {len(fail)}")
    print(f"  ○ EMPTY: {len(empty)}")
    print(f"  – SKIP:  {len(skip)}")
    print(f"  Celkem:  {len(results)}")

    if fail:
        print(f"\n  --- SELHALY ---")
        for r in fail:
            print(f"    {r['method']:<30} {r['rpc_id']:<10} {r['detail']}")

    if ok:
        print(f"\n  --- FUNGUJÍ ---")
        for r in ok:
            print(f"    {r['method']:<30} {r['rpc_id']:<10} {r['detail']}")

    if empty:
        print(f"\n  --- PRÁZDNÉ (auth OK, ale žádná data) ---")
        for r in empty:
            print(f"    {r['method']:<30} {r['rpc_id']:<10}")

    # JSON report
    report = {
        "timestamp": datetime.now().isoformat(),
        "summary": {
            "ok": len(ok),
            "fail": len(fail),
            "empty": len(empty),
            "skip": len(skip),
            "total": len(results),
        },
        "results": results,
    }

    if report_path:
        with open(report_path, "w") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        print(f"\n  Report uložen: {report_path}")
    else:
        default_path = Path(__file__).parent / "rpc_report.json"
        with open(default_path, "w") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        print(f"\n  Report uložen: {default_path}")


# ── Main ──

async def async_main():
    parser = argparse.ArgumentParser(description="NotebookLM RPC kompletní testovací pipeline")
    parser.add_argument("--get-only", action="store_true", help="Testuj jen GET metody")
    parser.add_argument("--reauth", action="store_true", help="Otevři browser pro nový login")
    parser.add_argument("--report", help="Cesta pro JSON report (default: scripts/rpc_report.json)")
    parser.add_argument("--notebook", "-n", help="Notebook ID (jinak autodetekce)")
    parser.add_argument("--skip-browser", action="store_true",
                        help="Použij cached cookies z ~/.notebooklm-cookies.json")
    args = parser.parse_args()

    print("╔══════════════════════════════════════════════╗")
    print("║  NotebookLM RPC Pipeline                     ║")
    print("╚══════════════════════════════════════════════╝")
    print()

    # Fáze 1: Cookies
    creds = None
    if args.skip_browser and os.path.exists(COOKIES_CACHE):
        print("── Fáze 1: Používám cached cookies ──")
        with open(COOKIES_CACHE) as f:
            creds = json.load(f)
        print(f"  Uloženo: {creds.get('saved_at', '?')}")
    else:
        headless = not args.reauth
        creds = await extract_cookies_from_browser(headless=headless)

    if not creds:
        print("\nCHYBA: Nepodařilo se získat cookies. Zkus --reauth")
        sys.exit(1)

    # Fáze 2: Auth test
    client = BatchExecuteClient(creds["cookies"], creds["csrf_token"], creds["session_id"])
    auth_ok, notebooks = test_auth(client)

    if not auth_ok:
        print("\nCHYBA: Auth selhala. Cookies jsou neplatné, zkus --reauth")
        sys.exit(1)

    # Určení notebook ID
    notebook_id = args.notebook
    if not notebook_id and creds.get("notebook_id"):
        notebook_id = creds["notebook_id"]
    if not notebook_id and notebooks:
        notebook_id = notebooks[0]["id"]
    if notebook_id:
        print(f"\n  Testovací notebook: {notebook_id}")

    # Fáze 3: Test metod
    results = test_methods(client, notebook_id, get_only=args.get_only)

    # Fáze 4: Report
    print_report(results, args.report)


def main():
    asyncio.run(async_main())


if __name__ == "__main__":
    main()
