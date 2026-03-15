# Analýza: Gemini Chat Web API + NotebookLM RPC srovnání

> Datum: 2026-03-15
> Účel: Reverse-engineered přístup k Gemini chat (gemini.google.com) pro Android appku

---

## 1. Klíčové zjištění

Google Gemini chat (gemini.google.com) používá **identický `batchexecute` RPC protokol** jako NotebookLM.
Naše Android appka už má 90% infrastruktury hotové (`RpcEncoder`, `RpcDecoder`, `AuthManager`, `EmbeddingDb`).
Napojení na Gemini chat vyžaduje pouze nový API klient s jinými endpointy a RPC ID.

**Autentizace je sdílená** — stejné Google cookies (`__Secure-1PSID`, `SID`) fungují pro obě služby.

---

## 2. Knihovny a reference

### Gemini Web Chat (reverse-engineered)

| Projekt | Popis | Odkaz |
|---------|-------|-------|
| **gemini-webapi** (HanaokaYuzu) | Nejkompletnější async Python klient pro gemini.google.com | [GitHub](https://github.com/HanaokaYuzu/Gemini-API) · [PyPI](https://pypi.org/project/gemini-webapi/) |
| **WebAI-to-API** (Amm1rr) | FastAPI server — Gemini web jako lokální API | [GitHub](https://github.com/Amm1rr/WebAI-to-API) |
| **gemini-web-wrapper** (eriksonssilva) | OpenAI-compatible wrapper pro Gemini web | [GitHub](https://github.com/eriksonssilva/gemini-web-wrapper) |
| **gemini-python-api** (Soumyabrataop) | Alternativní Python wrapper | [GitHub](https://github.com/Soumyabrataop/gemini-python-api) |

### NotebookLM (reverse-engineered)

| Projekt | Popis | Odkaz |
|---------|-------|-------|
| **notebooklm-py** (teng-lin) | Python SDK pro NotebookLM, 49 RPC metod | [GitHub](https://github.com/teng-lin/notebooklm-py) · [PyPI](https://pypi.org/project/notebooklm-py/) |

### batchexecute protokol (obecně)

| Zdroj | Popis | Odkaz |
|-------|-------|-------|
| **Deciphering batchexecute** (Ryan Kovatch) | Základní reference pro pochopení protokolu | [Medium](https://kovatch.medium.com/deciphering-google-batchexecute-74991e4e446c) |
| **pybatchexecute** | Python knihovna pro encode/decode batchexecute | [PyPI](https://pypi.org/project/pybatchexecute/) |
| **Working with batchexecute** (Penkov Vladimir) | Praktický průvodce protokolem | [Medium](https://medium.com/@penkov.vladimir/working-with-google-batchexecute-protocol-156b1c1bb670) |
| **Play Store reverse-engineering** (Benjamin Altpeter) | Příklad reverse-engineeringu batchexecute | [Blog](https://benjamin-altpeter.de/android-top-charts-reverse-engineering/) |

### Legacy (Bard éra, užitečné pro pochopení protokolu)

| Projekt | Popis | Odkaz |
|---------|-------|-------|
| **Bard-API** (dsdanielpark) | Původní Bard reverse-engineering | [GitHub](https://github.com/dsdanielpark/Bard-API) |
| **Bard** (acheong08) | První reverse-engineering Bard inference | [GitHub](https://github.com/acheong08/Bard) |

---

## 3. Srovnání protokolů: NotebookLM vs Gemini Chat

### Endpointy

| | NotebookLM | Gemini Chat |
|---|---|---|
| **batchexecute** | `notebooklm.google.com/_/LabsTailwindUi/data/batchexecute` | `gemini.google.com/_/BardChatUi/data/batchexecute` |
| **streaming** | `…/LabsTailwindOrchestrationService/GenerateFreeFormStreamed` | `…/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate` |
| **upload** | `notebooklm.google.com/upload/_/` | `content-push.googleapis.com/upload` |
| **cookie rotation** | — | `accounts.google.com/RotateCookies` |

### Autentizace

| Aspekt | NotebookLM (naše appka) | Gemini Chat |
|--------|------------------------|-------------|
| Metoda | Google cookies via WebView | Stejné Google cookies |
| Klíčové cookies | `SID`, `__Secure-1PSID`, `HSID`, `SSID` | `__Secure-1PSID`, `__Secure-1PSIDTS` |
| CSRF token | `SNlM0e` z HTML stránky | Stejný pattern — `SNlM0e` |
| Session ID | `FdrFJe` z HTML stránky | Stejný pattern — `FdrFJe` |
| Build label | `bl` parametr | `bl` parametr |
| **Sdílení cookies** | **ANO — stejné `.google.com` cookies fungují pro obě služby** | |

### Request formát (identický)

```
POST /batchexecute
Content-Type: application/x-www-form-urlencoded;charset=UTF-8

f.req=[[[rpc_id, json_params_string, null, "generic"]]]&at=CSRF_TOKEN&
```

Query parametry: `rpcids`, `source-path`, `f.sid`, `bl`, `rt=c`, `hl`

### Response formát (identický)

```
)]}'                          ← anti-XSSI prefix
<byte_count>
[["wrb.fr","rpc_id","..."]]  ← success
[["er","rpc_id",error_code]]  ← error
```

---

## 4. Gemini Chat — RPC metody

### Známé RPC IDs (ze zdrojového kódu gemini-webapi)

| RPC ID | Operace | Payload |
|--------|---------|---------|
| `MaZiqc` | **LIST_CHATS** — seznam všech konverzací | `[]` |
| `hNvQHb` | **READ_CHAT** — přečíst historii chatu | `[cid, max_turns, null, 1, [0], [4], null, 1]` |
| `GzXR5e` | **DELETE_CHAT** — smazat chat | `[cid]` |
| `CNgdBe` | **LIST_GEMS** — seznam Gems (custom persona) | `[]` |
| `oMH3Zd` | **CREATE_GEM** — vytvořit Gem | `[...]` |
| `kHv0Vd` | **UPDATE_GEM** — upravit Gem | `[...]` |
| `UXcSJb` | **DELETE_GEM** — smazat Gem | `[...]` |
| `ESY5D` | **BARD_ACTIVITY** — activity tracking | `[...]` |

### Streaming chat — StreamGenerate endpoint

```
POST gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate
```

Parametry: `bl`, `_reqid` (incrementing), `rt=c`, `f.sid`

### Modely (výběr přes HTTP headery)

| Model | Header `x-goog-ext-525001261-jspb` |
|-------|-------------------------------------|
| gemini-3.1-pro | `[1,null,null,null,"e6fa609c3fa255c0",null,null,0,[4],null,null,2]` |
| gemini-3.0-flash | `[1,null,null,null,"fbb127bbb056c959",null,null,0,[4],null,null,1]` |
| gemini-3.0-flash-thinking | `[1,null,null,null,"5bf011840784117a",null,null,0,[4],null,null,1]` |

### Conversation Turn struktura (z READ_CHAT response)

```
turn[0]     = [cid, rid]           ← metadata
turn[2][0][0] = user prompt text
turn[3][0][0] = first candidate
  candidate[0] = rcid
  candidate[1][0] = response text
  candidate[37][0][0] = thoughts (thinking modely)
turn[4]     = [epoch_seconds, ns]  ← timestamp
```

---

## 5. NotebookLM — kompletní RPC katalog (49 metod z notebooklm-py)

### Notebooks

| RPC ID | Operace | Naše appka |
|--------|---------|------------|
| `wXbhsf` | LIST_NOTEBOOKS | ✅ |
| `CCqFvf` | CREATE_NOTEBOOK | ✅ |
| `rLM1Ne` | GET_NOTEBOOK | ✅ |
| `s0tc2d` | RENAME_NOTEBOOK | ✅ |
| `WWINqb` | DELETE_NOTEBOOK | ✅ |
| `fejl7e` | REMOVE_RECENTLY_VIEWED | ❌ nové |

### Sources

| RPC ID | Operace | Naše appka |
|--------|---------|------------|
| `izAoDd` | ADD_SOURCE (URL, text, YouTube) | ✅ |
| `o4cbdc` | ADD_SOURCE_FILE (upload) | ❌ nové |
| `tGMBJ` | DELETE_SOURCE | ✅ |
| `hizoJc` | GET_SOURCE (fulltext) | ✅ |
| `FLmJqe` | REFRESH_SOURCE | ✅ |
| `yR9Yof` | CHECK_SOURCE_FRESHNESS | ❌ nové |
| `b7Wfje` | UPDATE_SOURCE | ✅ |
| `qXyaNe` | DISCOVER_SOURCES | ✅ (jako Ljjv0c) |

### Artifacts

| RPC ID | Operace | Naše appka |
|--------|---------|------------|
| `R7cb6c` | CREATE_ARTIFACT | ✅ |
| `gArtLc` | LIST_ARTIFACTS | ✅ |
| `V5N4be` | DELETE_ARTIFACT | ✅ |
| `v9rmvd` | GET_INTERACTIVE_HTML | ✅ |
| `rc3d8d` | RENAME_ARTIFACT | ❌ nové |
| `Krh3pd` | EXPORT_ARTIFACT | ❌ nové |
| `RGP97b` | SHARE_ARTIFACT | ❌ nové |
| `KmcKPe` | REVISE_SLIDE | ❌ nové |

### Summary & Research

| RPC ID | Operace | Naše appka |
|--------|---------|------------|
| `VfAZjd` | SUMMARIZE (notebook guide) | ✅ |
| `tr032e` | GET_SOURCE_GUIDE | ❌ nové |
| `ciyUvf` | GET_SUGGESTED_REPORTS | ❌ nové |
| `Ljjv0c` | START_FAST_RESEARCH | ✅ |
| `QA9ei` | START_DEEP_RESEARCH | ❌ nové |
| `e3bVqc` | POLL_RESEARCH | ✅ |
| `LBwxtb` | IMPORT_RESEARCH | ✅ |

### Notes & Mind Maps

| RPC ID | Operace | Naše appka |
|--------|---------|------------|
| `CYK0Xb` | CREATE_NOTE | ✅ |
| `cFji9` | GET_NOTES_AND_MIND_MAPS | ✅ |
| `cYAfTb` | UPDATE_NOTE | ✅ |
| `AH0mwd` | DELETE_NOTE | ✅ |
| `yyryJe` | GENERATE_MIND_MAP | ❌ nové |

### Chat & Conversation

| RPC ID | Operace | Naše appka |
|--------|---------|------------|
| `hPTbtc` | GET_LAST_CONVERSATION_ID | ✅ |
| `khqZz` | GET_CONVERSATION_TURNS | ✅ |

### Sharing & Settings

| RPC ID | Operace | Naše appka |
|--------|---------|------------|
| `QDyure` | SHARE_NOTEBOOK | ❌ nové |
| `JFMDGd` | GET_SHARE_STATUS | ❌ nové |
| `ZwVcOc` | GET_USER_SETTINGS | ✅ |
| `hT54vc` | SET_USER_SETTINGS | ❌ nové |

---

## 6. Implementační plán pro Gemini Chat integraci

### Co už máme a co využijeme

```
app/src/main/java/dev/jara/notebooklm/
├── rpc/
│   ├── RpcEncoder.kt       ← SDÍLENÝ (stejný formát)
│   ├── RpcDecoder.kt       ← SDÍLENÝ (stejný formát)
│   ├── RpcMethod.kt        ← rozšířit o Gemini RPC IDs
│   └── NotebookLmApi.kt    ← existující, neměnit
├── auth/
│   ├── AuthManager.kt      ← SDÍLENÝ (Google cookies fungují pro obě služby)
│   └── LoginActivity.kt    ← rozšířit o Gemini token fetch
├── search/
│   ├── OpenRouterEmbedding.kt  ← SDÍLENÝ
│   └── EmbeddingDb.kt          ← rozšířit o gemini_chat_embeddings tabulku
```

### Co je potřeba vytvořit

```
app/src/main/java/dev/jara/notebooklm/
├── rpc/
│   └── GeminiChatApi.kt     ← NOVÝ — Gemini chat klient
├── gemini/
│   ├── GeminiModels.kt      ← NOVÝ — data classes pro Gemini chat
│   └── GeminiChatScreen.kt  ← NOVÝ — UI pro Gemini chaty
```

### Kroky

1. **GeminiChatApi.kt** — nový API klient:
   - Base URL: `gemini.google.com/_/BardChatUi/data/batchexecute`
   - Token fetch z `gemini.google.com` (CSRF + session ID)
   - Metody: `listChats()`, `readChat(cid)`, `deleteChat(cid)`

2. **Sdílená autentizace** — ověřit, že cookies z NotebookLM loginu fungují i pro Gemini
   - Pokud ne → přidat druhý WebView login na `gemini.google.com`
   - Pokud ano → jen fetch CSRF tokenu z Gemini stránky

3. **Sumarizace chatů** — přečíst chat → embeddings → klasifikace
   - Využít existující `OpenRouterEmbedding` + `EmbeddingDb`
   - Nová tabulka `gemini_chat_embeddings`

4. **Facety** — rozšířit PMEST klasifikaci na Gemini chaty
   - Využít existující `notebook_facets` pattern

5. **Deduplikace** — cosine similarity mezi chaty
   - Threshold ~0.85 pro detekci duplicit

6. **UI** — nový screen v Compose
   - Seznam chatů s facety a similarity score
   - Akce: sumarizovat, smazat, deduplikovat

---

## 7. Rizika a omezení

- **Nestabilní API** — Google může kdykoli změnit RPC IDs nebo formát
- **Cookie rotace** — Gemini rotuje `__Secure-1PSIDTS` každých ~15-20 minut (gemini-webapi to řeší přes `RotateCookies` endpoint)
- **Rate limiting** — Error code 1060 = IP temporarily blocked
- **Model headery** — Gemini vyžaduje speciální `x-goog-ext-*` headery pro výběr modelu (NotebookLM to nemá)
- **Build label (`bl`)** — mění se s každým deployem, nutno extrahovat z HTML stránky
