#!/usr/bin/env python3
"""
NotebookLM batchexecute RPC tester.

Testuje RPC metody přímo proti notebooklm.google.com bez Android app.
Cookies se získávají z browseru (Chrome) nebo manuálně.

Použití:
    # Získej cookies z Chrome a otestuj listNotebooks
    python rpc_tester.py --method list_notebooks

    # Otestuj všechny GET metody
    python rpc_tester.py --test-all-get

    # Manuální RPC volání
    python rpc_tester.py --rpc wXbhsf --params '[null, 1, null, [2]]'

    # S manuálními cookies
    python rpc_tester.py --cookies "SID=xxx; HSID=yyy; ..." --method list_notebooks
"""

import argparse
import json
import re
import sys
import os
import time
from urllib.parse import urlencode, quote
from pathlib import Path

import requests

# ── RPC katalog ──

RPC_CATALOG = {
    # Projekty
    "list_notebooks":       {"id": "wXbhsf", "method": "ListRecentlyViewedProjects", "get": True},
    "create_notebook":      {"id": "CCqFvf", "method": "CreateProject", "get": False},
    "get_notebook":         {"id": "rLM1Ne", "method": "GetProject", "get": True},
    "rename_notebook":      {"id": "s0tc2d", "method": "MutateProject", "get": False},
    "delete_notebook":      {"id": "WWINqb", "method": "DeleteProjects", "get": False},
    "remove_recent":        {"id": "fejl7e", "method": "RemoveRecentlyViewedProject", "get": False},
    "copy_notebook":        {"id": "te3DCe", "method": "CopyProject", "get": False},
    "get_analytics":        {"id": "AUrzMb", "method": "GetProjectAnalytics", "get": True},
    "list_featured":        {"id": "ub2Bae", "method": "ListFeaturedProjects", "get": True},

    # Zdroje
    "add_source":           {"id": "izAoDd", "method": "AddSources", "get": False},
    "delete_source":        {"id": "tGMBJ",  "method": "DeleteSources", "get": False},
    "get_source":           {"id": "hizoJc", "method": "LoadSource", "get": True},
    "mutate_source":        {"id": "b7Wfje", "method": "MutateSource", "get": False},
    "refresh_source":       {"id": "FLmJqe", "method": "RefreshSource", "get": False},
    "act_on_sources":       {"id": "yyryJe", "method": "ActOnSources", "get": True},
    "check_freshness":      {"id": "yR9Yof", "method": "CheckSourceFreshness", "get": True},
    "add_tentative":        {"id": "o4cbdc", "method": "AddTentativeSources", "get": False},

    # Source Discovery
    "discover_sources":     {"id": "Es3dTe", "method": "DiscoverSources", "get": True},
    "discover_async":       {"id": "QA9ei",  "method": "DiscoverSourcesAsync", "get": False},
    "discover_manifold":    {"id": "Ljjv0c", "method": "DiscoverSourcesManifold", "get": False},
    "list_discover_jobs":   {"id": "e3bVqc", "method": "ListDiscoverSourcesJob", "get": True},
    "finish_discover":      {"id": "LBwxtb", "method": "FinishDiscoverSourcesRun", "get": False},
    "cancel_discover":      {"id": "Zbrupe", "method": "CancelDiscoverSourcesJob", "get": False},

    # Chat
    "chat_streamed":        {"id": "laWbsf", "method": "GenerateFreeFormStreamed", "get": False,
                             "streaming": True},
    "list_chat_sessions":   {"id": "hPTbtc", "method": "ListChatSessions", "get": True},
    "list_chat_turns":      {"id": "khqZz",  "method": "ListChatTurns", "get": True},
    "delete_chat_turns":    {"id": "J7Gthc", "method": "DeleteChatTurns", "get": False},

    # Artefakty
    "create_artifact":      {"id": "R7cb6c", "method": "CreateArtifact", "get": False},
    "generate_artifact":    {"id": "Rytqqe", "method": "GenerateArtifact", "get": False},
    "get_artifact":         {"id": "v9rmvd", "method": "GetArtifact", "get": True},
    "list_artifacts":       {"id": "gArtLc", "method": "ListArtifacts", "get": True},
    "update_artifact":      {"id": "rc3d8d", "method": "UpdateArtifact", "get": False},
    "delete_artifact":      {"id": "V5N4be", "method": "DeleteArtifact", "get": False},
    "derive_artifact":      {"id": "KmcKPe", "method": "DeriveArtifact", "get": False},
    "artifact_customization": {"id": "sqTeoe", "method": "GetArtifactCustomizationChoices", "get": True},
    "artifact_user_state":  {"id": "ulBSjf", "method": "GetArtifactUserState", "get": True},
    "upsert_artifact_state": {"id": "Fxmvse", "method": "UpsertArtifactUserState", "get": False},

    # Poznámky
    "get_notes":            {"id": "cFji9",  "method": "GetNotes", "get": True},
    "create_note":          {"id": "CYK0Xb", "method": "CreateNote", "get": False},
    "mutate_note":          {"id": "cYAfTb", "method": "MutateNote", "get": False},
    "delete_notes":         {"id": "AH0mwd", "method": "DeleteNotes", "get": False},

    # AI generování
    "notebook_guide":       {"id": "VfAZjd", "method": "GenerateNotebookGuide", "get": True},
    "doc_guides":           {"id": "tr032e", "method": "GenerateDocumentGuides", "get": True},
    "prompt_suggestions":   {"id": "otmP3b", "method": "GeneratePromptSuggestions", "get": True},
    "report_suggestions":   {"id": "ciyUvf", "method": "GenerateReportSuggestions", "get": True},
    "writing_function":     {"id": "likKIe", "method": "ExecuteWritingFunction", "get": False},
    "magic_view":           {"id": "uK8f7c", "method": "GenerateMagicView", "get": True},
    "get_magic_view":       {"id": "rtY7md", "method": "GetMagicView", "get": True},
    "magic_index":          {"id": "XpqOp",  "method": "GetMagicIndex", "get": True},
    "list_models":          {"id": "EnujNd", "method": "ListModelOptions", "get": True},

    # Účet
    "get_account":          {"id": "ZwVcOc", "method": "GetOrCreateAccount", "get": False},
    "mutate_account":       {"id": "hT54vc", "method": "MutateAccount", "get": False},
    "access_token":         {"id": "preRPe", "method": "GenerateAccessToken", "get": True},

    # Sdílení
    "share_project":        {"id": "QDyure", "method": "ShareProject", "get": False},
    "shared_details":       {"id": "JFMDGd", "method": "GetProjectDetails", "get": True},
    "access_request":       {"id": "n3dkHd", "method": "CreateAccessRequest", "get": False},

    # Export & Feedback
    "export_drive":         {"id": "Krh3pd", "method": "ExportToDrive", "get": False},
    "feedback":             {"id": "uNyJKe", "method": "SubmitFeedback", "get": False},
    "report_content":       {"id": "OmVMXc", "method": "ReportContent", "get": False},
}

BATCHEXECUTE_URL = "https://notebooklm.google.com/_/LabsTailwindUi/data/batchexecute"

# ── Předdefinované parametry pro testování ──

def get_test_params(method_name: str, notebook_id: str = None) -> list:
    """Vrátí testovací parametry pro danou metodu."""
    nb = notebook_id

    params_map = {
        "list_notebooks":       [None, 1, None, [2]],
        "get_notebook":         [nb] if nb else None,
        "list_artifacts":       [nb, [2]] if nb else None,
        "get_notes":            [nb] if nb else None,
        "notebook_guide":       [nb, [2]] if nb else None,
        "list_chat_sessions":   [nb] if nb else None,
        "list_chat_turns":      [[], None, None, None, 50],
        "prompt_suggestions":   [nb, None, None, 3] if nb else None,
        "artifact_customization": [],
        "list_models":          [],
        "get_account":          [],
        "access_token":         [],
        "list_featured":        [None, 20],
        "list_discover_jobs":   [nb] if nb else None,
        "check_freshness":      [nb] if nb else None,
        "act_on_sources":       [nb] if nb else None,
        "doc_guides":           [nb] if nb else None,
        "report_suggestions":   [nb] if nb else None,
        "magic_index":          [nb] if nb else None,
        "magic_view":           [nb] if nb else None,
        "get_magic_view":       [nb] if nb else None,
        "artifact_user_state":  [nb] if nb else None,
    }
    return params_map.get(method_name)


# ── Batchexecute protokol ──

class BatchExecuteClient:
    """Klient pro Google batchexecute RPC protokol."""

    def __init__(self, cookies: str, csrf_token: str, session_id: str):
        self.cookies = cookies
        self.csrf_token = csrf_token
        self.session_id = session_id
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) "
                          "AppleWebKit/537.36 (KHTML, like Gecko) "
                          "Chrome/131.0.0.0 Mobile Safari/537.36",
            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            "Cookie": cookies,
        })

    def call(self, rpc_id: str, params: list, source_path: str = "/") -> dict:
        """Provede batchexecute RPC volání. Vrátí {"raw": str, "parsed": Any, "error": str|None}."""
        params_json = json.dumps(params)

        inner = [rpc_id, params_json, None, "generic"]
        f_req = json.dumps([[inner]])

        body = urlencode({
            "f.req": f_req,
            "at": self.csrf_token,
        }) + "&"

        url_params = urlencode({
            "rpcids": rpc_id,
            "source-path": source_path,
            "f.sid": self.session_id,
            "hl": "en",
            "rt": "c",
        })
        url = f"{BATCHEXECUTE_URL}?{url_params}"

        resp = self.session.post(url, data=body)
        raw = resp.text

        if resp.status_code != 200:
            return {"raw": raw[:500], "parsed": None, "error": f"HTTP {resp.status_code}"}

        try:
            parsed = self._decode(raw, rpc_id)
            return {"raw": raw[:2000], "parsed": parsed, "error": None}
        except Exception as e:
            return {"raw": raw[:2000], "parsed": None, "error": str(e)}

    def call_streaming(self, rpc_id: str, params: list, source_path: str = "/") -> dict:
        """Volání pro streaming endpoint (GenerateFreeFormStreamed)."""
        params_json = json.dumps(params)
        f_req = json.dumps([None, params_json])

        body = urlencode({
            "f.req": f_req,
            "at": self.csrf_token,
        }) + "&"

        url = (
            "https://notebooklm.google.com/_/LabsTailwindUi/data/"
            "google.internal.labs.tailwind.orchestration.v1."
            "LabsTailwindOrchestrationService/GenerateFreeFormStreamed"
            f"?hl=en&rt=c&f.sid={quote(self.session_id)}"
        )

        resp = self.session.post(url, data=body, stream=True)
        raw = resp.text

        if resp.status_code != 200:
            return {"raw": raw[:500], "parsed": None, "error": f"HTTP {resp.status_code}"}

        # Extrahuj odpovědi ze streamu
        answers = []
        for line in raw.splitlines():
            if "wrb.fr" not in line:
                continue
            try:
                parsed = json.loads(line)
                items = parsed if isinstance(parsed[0], list) else [parsed]
                for item in items:
                    if item[0] == "wrb.fr":
                        inner = json.loads(item[2])
                        text = inner[0][0] if inner and inner[0] else None
                        if text:
                            answers.append(text)
            except (json.JSONDecodeError, IndexError, TypeError):
                continue

        best = max(answers, key=len) if answers else None
        return {"raw": raw[:2000], "parsed": best, "error": None if best else "Odpověď nenalezena"}

    def _decode(self, raw: str, rpc_id: str):
        """Dekóduje batchexecute odpověď."""
        # Odstraň anti-XSSI prefix
        if raw.startswith(")]}'"):
            raw = raw[raw.index('\n') + 1:]

        # Parsuj chunked response
        lines = raw.strip().split('\n')
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            if not line:
                i += 1
                continue

            # Číslo = délka dalšího chunku
            try:
                int(line)
                i += 1
                if i < len(lines):
                    chunk = lines[i]
                    result = self._extract_rpc(chunk, rpc_id)
                    if result is not None:
                        return result
                i += 1
            except ValueError:
                result = self._extract_rpc(line, rpc_id)
                if result is not None:
                    return result
                i += 1

        return None

    def _extract_rpc(self, json_str: str, rpc_id: str):
        """Extrahuje RPC výsledek z JSON chunku."""
        try:
            arr = json.loads(json_str)
        except json.JSONDecodeError:
            return None

        items = arr if isinstance(arr[0], list) else [arr]
        for item in items:
            if len(item) < 3:
                continue
            if item[0] == "wrb.fr" and item[1] == rpc_id:
                if item[2] is None:
                    return None
                try:
                    return json.loads(item[2])
                except (json.JSONDecodeError, TypeError):
                    return item[2]
            if item[0] == "er" and item[1] == rpc_id:
                raise Exception(f"RPC error: {item[2] if len(item) > 2 else 'unknown'}")
        return None


# ── Cookies z Chrome ──

def get_cookies_from_chrome() -> str | None:
    """Zkusí získat cookies z Chrome přes sqlite3 (Linux, nezašifrované)."""
    # Na Linuxu jsou v ~/.config/google-chrome/Default/Cookies (SQLite, šifrované)
    # Jednodušší: exportovat ručně z DevTools
    return None


def get_cookies_from_file(path: str = None) -> dict | None:
    """Načte cookies + tokeny ze souboru."""
    if path is None:
        path = os.path.expanduser("~/.notebooklm-cookies.json")
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)


def fetch_tokens_from_page(cookies: str) -> dict | None:
    """Stáhne NotebookLM stránku a extrahuje CSRF token + session ID z HTML."""
    resp = requests.get(
        "https://notebooklm.google.com/",
        headers={
            "Cookie": cookies,
            "User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) "
                          "AppleWebKit/537.36",
        },
        allow_redirects=True,
    )

    if resp.status_code != 200:
        print(f"CHYBA: HTTP {resp.status_code} při stahování stránky")
        return None

    html = resp.text

    # CSRF token: WIZ_global_data => "SNlM0e":"TOKEN"
    csrf_match = re.search(r'"SNlM0e"\s*:\s*"([^"]+)"', html)
    if not csrf_match:
        print("CHYBA: CSRF token nenalezen v HTML")
        return None

    # Session ID: WIZ_global_data => "FdrFJe":"SESSION_ID"
    sid_match = re.search(r'"FdrFJe"\s*:\s*"([^"]+)"', html)
    if not sid_match:
        print("CHYBA: Session ID nenalezen v HTML")
        return None

    return {
        "csrf_token": csrf_match.group(1),
        "session_id": sid_match.group(1),
    }


def save_cookies_interactive():
    """Interaktivní uložení cookies do souboru."""
    print("=== Uložení NotebookLM cookies ===")
    print()
    print("1. Otevři Chrome → notebooklm.google.com (přihlášený)")
    print("2. F12 → Console → zadej:")
    print("   document.cookie")
    print("3. Zkopíruj výsledek sem:")
    print()
    cookies = input("Cookies: ").strip().strip('"').strip("'")
    if not cookies:
        print("Žádné cookies, ukončuji.")
        return

    print("\nStahuji tokeny z NotebookLM stránky...")
    tokens = fetch_tokens_from_page(cookies)
    if not tokens:
        print("Nepodařilo se získat tokeny. Zkontroluj cookies.")
        return

    data = {
        "cookies": cookies,
        "csrf_token": tokens["csrf_token"],
        "session_id": tokens["session_id"],
        "saved_at": time.strftime("%Y-%m-%d %H:%M:%S"),
    }

    path = os.path.expanduser("~/.notebooklm-cookies.json")
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
    os.chmod(path, 0o600)
    print(f"\nUloženo do {path}")
    print(f"CSRF: {tokens['csrf_token'][:20]}...")
    print(f"SID:  {tokens['session_id']}")


# ── Hlavní logika ──

def test_method(client: BatchExecuteClient, method_name: str, notebook_id: str = None):
    """Otestuje jednu RPC metodu."""
    if method_name not in RPC_CATALOG:
        print(f"  CHYBA: Neznámá metoda '{method_name}'")
        return

    info = RPC_CATALOG[method_name]
    rpc_id = info["id"]
    params = get_test_params(method_name, notebook_id)

    if params is None:
        print(f"  {method_name} ({rpc_id} → {info['method']}): PŘESKOČENO — potřebuje notebook_id")
        return

    print(f"\n{'='*60}")
    print(f"  {method_name}")
    print(f"  RPC ID: {rpc_id}")
    print(f"  gRPC:   {info['method']}")
    print(f"  Type:   {'GET' if info['get'] else 'POST'}")
    print(f"  Params: {json.dumps(params, ensure_ascii=False)[:100]}")

    source_path = f"/notebook/{notebook_id}" if notebook_id else "/"

    if info.get("streaming"):
        result = client.call_streaming(rpc_id, params, source_path)
    else:
        result = client.call(rpc_id, params, source_path)

    if result["error"]:
        print(f"  CHYBA:  {result['error']}")
        print(f"  Raw:    {result['raw'][:300]}")
    else:
        parsed = result["parsed"]
        if parsed is None:
            print(f"  Výsledek: null (prázdná odpověď)")
        else:
            formatted = json.dumps(parsed, ensure_ascii=False, indent=2)
            if len(formatted) > 1000:
                print(f"  Výsledek ({len(formatted)} chars):")
                print(f"  {formatted[:1000]}...")
            else:
                print(f"  Výsledek:")
                print(f"  {formatted}")

    return result


def main():
    parser = argparse.ArgumentParser(description="NotebookLM batchexecute RPC tester")
    parser.add_argument("--setup", action="store_true", help="Interaktivní uložení cookies")
    parser.add_argument("--cookies", help="Cookie string (nebo načte z ~/.notebooklm-cookies.json)")
    parser.add_argument("--method", help="Testovaná metoda (viz --list)")
    parser.add_argument("--rpc", help="Přímé RPC ID (ruční volání)")
    parser.add_argument("--params", help="JSON parametry pro --rpc nebo --method")
    parser.add_argument("--notebook", "-n", help="Notebook ID pro metody co ho potřebují")
    parser.add_argument("--test-all-get", action="store_true", help="Otestuj všechny GET metody")
    parser.add_argument("--test-all", action="store_true", help="Otestuj VŠECHNY metody (pozor!)")
    parser.add_argument("--list", action="store_true", help="Vypíše všechny dostupné metody")
    parser.add_argument("--source-path", default="/", help="Source path pro URL")
    args = parser.parse_args()

    # Výpis metod
    if args.list:
        print(f"{'Jméno':<25} {'RPC ID':<10} {'GET/POST':<10} {'gRPC metoda'}")
        print("-" * 80)
        for name, info in sorted(RPC_CATALOG.items()):
            t = "GET" if info["get"] else "POST"
            s = " [STREAM]" if info.get("streaming") else ""
            print(f"{name:<25} {info['id']:<10} {t:<10} {info['method']}{s}")
        return

    # Setup
    if args.setup:
        save_cookies_interactive()
        return

    # Načti credentials
    if args.cookies:
        tokens = fetch_tokens_from_page(args.cookies)
        if not tokens:
            sys.exit(1)
        creds = {"cookies": args.cookies, **tokens}
    else:
        creds = get_cookies_from_file()
        if not creds:
            print("Cookies nenalezeny. Spusť: python rpc_tester.py --setup")
            sys.exit(1)

    client = BatchExecuteClient(creds["cookies"], creds["csrf_token"], creds["session_id"])
    nb = args.notebook

    # Přímé RPC volání
    if args.rpc:
        params = json.loads(args.params) if args.params else []
        result = client.call(args.rpc, params, args.source_path)
        print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
        return

    # Test jedné metody
    if args.method:
        params_override = json.loads(args.params) if args.params else None
        if params_override is not None:
            info = RPC_CATALOG.get(args.method)
            if not info:
                print(f"Neznámá metoda: {args.method}")
                sys.exit(1)
            result = client.call(info["id"], params_override, args.source_path)
            print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
        else:
            test_method(client, args.method, nb)
        return

    # Test všech GET metod
    if args.test_all_get:
        print("=== Test všech GET metod ===")
        results = {}
        for name, info in sorted(RPC_CATALOG.items()):
            if not info["get"]:
                continue
            result = test_method(client, name, nb)
            results[name] = "OK" if result and not result.get("error") else "FAIL"
            time.sleep(0.5)  # Rate limiting

        print(f"\n{'='*60}")
        print("SOUHRN:")
        for name, status in sorted(results.items()):
            icon = "✓" if status == "OK" else "✗"
            print(f"  {icon} {name}")
        return

    # Test všech metod
    if args.test_all:
        print("=== Test VŠECH metod (opatrně!) ===")
        results = {}
        for name, info in sorted(RPC_CATALOG.items()):
            if info.get("streaming"):
                print(f"\n  PŘESKOČENO (streaming): {name}")
                continue
            result = test_method(client, name, nb)
            results[name] = "OK" if result and not result.get("error") else "FAIL"
            time.sleep(0.5)

        print(f"\n{'='*60}")
        print("SOUHRN:")
        for name, status in sorted(results.items()):
            icon = "✓" if status == "OK" else "✗"
            print(f"  {icon} {name}")
        return

    # Default: help
    parser.print_help()


if __name__ == "__main__":
    main()
