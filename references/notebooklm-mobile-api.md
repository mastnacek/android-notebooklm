# NotebookLM — Kompletní API reference

Extrahováno z:
1. APK `com.google.android.apps.labs.language.tailwind` (Flutter) — `strings` na Dart AOT snapshot
2. Web JS bundle `boq-labs-tailwind.LabsTailwindUi` (2.5MB) — RPC definice z `_.Tz()` registrací

## Transport & Auth

### gRPC Server
- **Produkce:** `notebooklm-pa.googleapis.com` (port 443, TLS/HTTP2)
- **Staging:** `staging-notebooklm-pa.sandbox.googleapis.com`
- **Autopush:** `autopush-notebooklm-pa.sandbox.googleapis.com`

### Autentizace
App používá **Google SSO** přes Android Account Manager + Google Play Services:

1. **Sign-in:** `GoogleSignInClient` → Google Sign-In flow → email + OAuth token
2. **Token:** `GoogleAuthUtil.getToken(context, email, scopes)` → OAuth2 access token
3. **gRPC auth:** Token se posílá jako `Bearer` v auth header přes `ChannelCredentials`
4. **Refresh:** Automatický refresh přes `UserRecoverableAuthException` handling
5. **Storage:** Account data v `SharedPreferences("com.google.android.flutter.plugins.ssoauth")`
6. **Token provider:** `_tokenProvider` → `serverTokenProvider` → přidává token do gRPC metadata

### OAuth2 Scopes
```
https://www.googleapis.com/auth/userinfo.email
https://www.googleapis.com/auth/userinfo.profile
https://www.googleapis.com/auth/drive
https://www.googleapis.com/auth/photos.image.readonly
https://www.googleapis.com/auth/notifications
https://www.googleapis.com/auth/cclog
https://www.googleapis.com/auth/experimentsandconfigs
https://www.googleapis.com/auth/supportcontent
https://www.googleapis.com/auth/account_settings_mobile
```

### gRPC Headers
```
Authorization: Bearer <access_token>
X-Goog-AuthUser: <user_index>
x-goog-ext-353267353-bin: <binary_metadata>
Content-Type: application/grpc
User-Agent: <flutter_user_agent>
```

### Dart gRPC Stack
```
TailwindRpcService
  → TailwindRpcClient (tailwind_rpc_client.dart)
    → GrpcHttp2Channel (package:grpc + package:rpc_client)
      → HTTP/2 TLS connection to notebooklm-pa.googleapis.com:443
        → ChannelCredentials (Bearer token from SSO)
          → CallOptions + TailwindRpcOptions + GoogleRpcOptions
            → RequestContextHandler → CuiAttributionHelper
```

### File Upload (Scotty)
Soubory se uploadují přes Google Scotty protocol:
1. Inicializace: POST → získání `X-Goog-Upload-Url`
2. Upload: PUT na upload URL s `X-Goog-Upload-Command: upload, finalize`
3. Headers: `X-Goog-Upload-Content-Length`, `X-Goog-Upload-File-Name`

### Klíčový rozdíl: Web vs Mobile
| | Web (naše app) | Mobile (Google app) |
|---|---|---|
| Transport | batchexecute HTTP POST | gRPC over HTTP/2 |
| Auth | Cookie-based (SID, HSID, SSID) | OAuth2 Bearer token |
| Endpoint | notebooklm.google.com/_/LabsTailwindUi/data/batchexecute | notebooklm-pa.googleapis.com:443 |
| Serialization | JSON arrays (batchexecute format) | Protocol Buffers |
| Service name | LabsTailwindUi | LabsTailwindOrchestrationService |

### Jak bychom mohli využít gRPC API
1. **Získat OAuth2 token** — naše app už má Google cookies z WebView login
2. **Alternativa:** Použít cookies pro batchexecute (stávající flow) NEBO
3. **Vyměnit cookie za OAuth token** — `accounts.google.com/o/oauth2/v2/auth` s naší session
4. **Přímé gRPC volání** — Ktor/OkHttp gRPC klient na `notebooklm-pa.googleapis.com`
5. **Protobuf schéma** — potřebujeme `.proto` soubory (nemáme, ale známe názvy metod + request/response typy)

## gRPC Services

### LabsTailwindOrchestrationService
`google.internal.labs.tailwind.orchestration.v1.LabsTailwindOrchestrationService`

### LabsTailwindSharingService
`labs.language.tailwind.sharing.LabsTailwindSharingService`

### DasherGrowthPromotionService
Interní Google service pro doporučení a eventy.

## Kompletní batchexecute RPC mapa (62 metod)

Extrahováno z JS bundlu — pattern: `new _.Tz("RPC_ID", ResponseClass, [_.Mz, isGET, _.Oz, "/Service.Method"])`

### Projekty (Notebooky)
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `CCqFvf` | CreateProject | POST | Vytvoření nového notebooku |
| `rLM1Ne` | GetProject | GET | Detail notebooku |
| `s0tc2d` | MutateProject | POST | Úprava notebooku (přejmenování, nastavení) |
| `WWINqb` | DeleteProjects | POST | Smazání notebooků (batch) |
| `wXbhsf` | ListRecentlyViewedProjects | GET | Nedávno zobrazené notebooky |
| `fejl7e` | RemoveRecentlyViewedProject | POST | Odebrání z nedávných |
| `te3DCe` | CopyProject | POST | Kopírování notebooku |
| `AUrzMb` | GetProjectAnalytics | GET | Analytika notebooku |
| `ub2Bae` | ListFeaturedProjects | GET | Doporučené/featured notebooky |
| `DemIHe` | UpdateFeaturedNotebookStatus | POST | Aktualizace featured statusu |

### Zdroje (Sources)
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `izAoDd` | AddSources | POST | Přidání zdrojů do notebooku |
| `tGMBJ` | DeleteSources | POST | Smazání zdrojů |
| `hizoJc` | LoadSource | GET | Načtení obsahu zdroje |
| `b7Wfje` | MutateSource | POST | Úprava zdroje |
| `FLmJqe` | RefreshSource | POST | Obnovení zdroje |
| `yyryJe` | ActOnSources | GET | Akce nad zdroji (batch) |
| `yR9Yof` | CheckSourceFreshness | GET | Kontrola aktuálnosti zdroje |
| `o4cbdc` | AddTentativeSources | POST | Přidání zdrojů v tentativním stavu |

### Source Discovery
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `Es3dTe` | DiscoverSources | GET | Objevení zdrojů (URL, web scraping) |
| `QA9ei` | DiscoverSourcesAsync | POST | Async discovery zdrojů |
| `Ljjv0c` | DiscoverSourcesManifold | POST | Manifold discovery zdrojů |
| `e3bVqc` | ListDiscoverSourcesJob | GET | Seznam discovery jobů |
| `LBwxtb` | FinishDiscoverSourcesRun | POST | Dokončení discovery session |
| `Zbrupe` | CancelDiscoverSourcesJob | POST | Zrušení discovery jobu |

### Chat
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `laWbsf` | GenerateFreeFormStreamed | POST | **Streaming chat** (hlavní chat) |
| `hPTbtc` | ListChatSessions | GET | Seznam chat sessions |
| `khqZz` | ListChatTurns | GET | Historie chatu |
| `J7Gthc` | DeleteChatTurns | POST | Smazání chatových zpráv |

### Artefakty (Studio tab)
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `R7cb6c` | CreateArtifact | POST | Vytvoření artefaktu |
| `Rytqqe` | GenerateArtifact | POST | Generování artefaktu (AI) |
| `v9rmvd` | GetArtifact | GET | Detail artefaktu |
| `gArtLc` | ListArtifacts | GET | Seznam artefaktů |
| `rc3d8d` | UpdateArtifact | POST | Úprava artefaktu |
| `V5N4be` | DeleteArtifact | POST | Smazání artefaktu |
| `KmcKPe` | DeriveArtifact | POST | Odvození nového artefaktu z existujícího |
| `sqTeoe` | GetArtifactCustomizationChoices | GET | Dostupné customizace |
| `ulBSjf` | GetArtifactUserState | GET | Uživatelský stav artefaktu |
| `Fxmvse` | UpsertArtifactUserState | POST | Uložení uživatelského stavu |

### Poznámky & Průvodce
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `cFji9` | GetNotes | GET | Získání poznámek |
| `CYK0Xb` | CreateNote | POST | Vytvoření poznámky |
| `cYAfTb` | MutateNote | POST | Úprava poznámky |
| `AH0mwd` | DeleteNotes | POST | Smazání poznámek |
| `VfAZjd` | GenerateNotebookGuide | GET | AI průvodce notebookem |
| `tr032e` | GenerateDocumentGuides | GET | AI průvodce dokumentem |

### AI generování
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `otmP3b` | GeneratePromptSuggestions | GET | AI návrhy promptů |
| `ciyUvf` | GenerateReportSuggestions | GET | AI návrhy reportů |
| `likKIe` | ExecuteWritingFunction | POST | Spuštění psací funkce |
| `uK8f7c` | GenerateMagicView | GET | Generování magic view |
| `rtY7md` | GetMagicView | GET | Získání magic view |
| `XpqOp` | GetMagicIndex | GET | Získání magic indexu |
| `EnujNd` | ListModelOptions | GET | Seznam AI modelů |

### Účet & Auth
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `ZwVcOc` | GetOrCreateAccount | POST | Získání/vytvoření účtu |
| `hT54vc` | MutateAccount | POST | Úprava nastavení účtu |
| `preRPe` | GenerateAccessToken | GET | Generování přístupového tokenu |

### Sdílení (LabsTailwindSharingService)
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `QDyure` | ShareProject | POST | Sdílení notebooku |
| `JFMDGd` | GetProjectDetails | GET | Detail sdíleného projektu |
| `n3dkHd` | CreateAccessRequest | POST | Žádost o přístup |

### Export & Feedback
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `Krh3pd` | ExportToDrive | POST | Export do Google Drive |
| `uNyJKe` | SubmitFeedback | POST | Odeslání zpětné vazby |
| `OmVMXc` | ReportContent | POST | Nahlášení obsahu |

### Google Interní (DasherGrowthPromotionService)
| RPC ID | Metoda | GET/POST | Popis |
|--------|--------|----------|-------|
| `ozz5Z` | FetchRecommendations | GET | Doporučení |
| `i1CNXc` | RecordEvents | POST | Záznam událostí |

### Live Audio (WebRTC) — jen v mobilní app
| Metoda | Popis |
|--------|-------|
| GetIceConfig | ICE konfigurace pro WebRTC |
| SendSdpOffer | Odeslání SDP nabídky |

## Typy artefaktů (ARTIFACT_TYPE_*)
| Typ | Popis |
|-----|-------|
| AUDIO_OVERVIEW | Audio přehled (podcast) |
| EXPLAINER_VIDEO | Vysvětlující video |
| FLASHCARDS | Kartičky (easy/medium/hard) |
| INFOGRAPHIC | Infografika (s výběrem stylů) |
| QUIZ | Kvíz (easy/medium/hard) |
| SLIDES | Prezentace (short/medium/long/dynamic) |
| TAILORED_REPORT | Přizpůsobená zpráva |
| APP | Obecný app artefakt |
| TABLE | Tabulka |
| MINDMAP | Myšlenková mapa |
| FANTASY_MAP | Fantasy mapa |

### Video styly (VIDEO_OVERVIEW_STYLE_*)
anime, classic, auto_select, heritage, kawaii, papercraft, risographic, watercolor, whiteboard

### Infographic styly (STYLE_*)
anime, auto, bento_grid, bricks, claymation, editorial, kawaii, professional, scientific, sketch_note, storyboard

### Flashcards obtížnost (FLASHCARDS_DIFFICULTY_*)
easy, medium, hard

### Quiz obtížnost (QUIZ_DIFFICULTY_*)
easy, medium, hard

### Slides délka (SLIDE_DECK_LENGTH_*)
short, medium, long, dynamic

## Typy zdrojů (SOURCE_CONTENT_TYPE_*)
| Typ | Popis |
|-----|-------|
| AUDIO | Audio soubor |
| CSV | CSV tabulka |
| DRIVE | Google Drive |
| EPUB | E-book |
| EXCEL | Excel soubor |
| GEMINI_CHAT | Chat z Gemini |
| GMAIL | E-mail z Gmailu |
| GOOGLE_DOC | Google Docs |
| GOOGLE_SHEET | Google Sheets |
| GOOGLE_SLIDES | Google Slides |
| IMAGE | Obrázek |
| MARKDOWN | Markdown |
| PDF | PDF dokument |
| POWERPOINT | PowerPoint |
| TEXT | Prostý text |
| URL | Webová stránka |
| WORD | Word dokument |
| YOUTUBE_VIDEO | YouTube video |

### Status zdroje (SOURCE_STATUS_*)
complete, error, pending, pending_deletion, tentative, unspecified

## Sdílení a role (PROJECT_ROLE_*)
| Role | Popis |
|------|-------|
| OWNER | Vlastník notebooku |
| WRITER | Může editovat |
| READER | Může pouze číst |
| NOT_SHARED | Nesdílený |

## Předplatné tiery (TIER_*)
| Tier | Popis |
|------|-------|
| FREE | Zdarma |
| PLUS | Plus |
| PRO | Pro |
| ULTRA | Ultra |
| GEMNOVA | GemNova (interní?) |

## WebRTC protokol (Live Audio)
Protobuf zprávy přes DataChannel:

| Zpráva | Popis |
|--------|-------|
| AgentCommsUserMessage | Uživatelská zpráva (oneof type) |
| DataChannelMessage | Obecná zpráva (s kompresí) |
| MicrophoneEvent | MUTE/UNMUTE/AGENT_FORCE_MUTE/UNMUTE |
| TtsEvent | Text-to-speech event |
| SendAudioEvent | Odeslání audio dat |
| PlaybackEvent | Přehrávání odpovědi |
| StatusMessage | Stavová zpráva |
| ErrorEvent | Chybová zpráva |
| Utterance | Přepis řeči |
| DataChannelChunk | Chunkované přenosy |

### MicrophoneEvent typy
USER_MUTED, USER_UNMUTED, AGENT_FORCE_MUTED, AGENT_FORCE_UNMUTED,
USER_REQUEST_UNMUTED, USER_REQUEST_MUTED

## Protobuf balíčky
`labs_language.tailwind.common.protos`:
- api_enums — enum definice API
- app_api_capabilities — schopnosti API
- discover_sources_enums — enum pro discovery zdrojů
- doc_enums — enum pro dokumenty
- chat_history — historie chatu
- metadata — metadata
- notifications — push notifikace
- premium_tier — předplatné
- projects — projekty
- provenance — provenance zdrojů

## Architektura aplikace (Dart packages)
```
labs.language.tailwind.mobile.app/
├── analytics/          — sledování událostí
├── content_picker/     — výběr obsahu (soubory, URL, YouTube)
├── error_handling/     — zpracování chyb
├── feature_flags/      — feature flags
├── features/
│   ├── artifacts/      — artefakty (repository, viewer, audio player)
│   └── projects/       — projekty (manage access, share, request access)
├── feedback/           — zpětná vazba
├── l10n/               — lokalizace
├── models/             — datové modely (app_artifact, audio_playback)
├── navigation/         — navigace
├── overlay/            — overlay UI
├── pages/
│   ├── app_artifact_page/           — obecný artefakt
│   ├── customize_artifact_page/     — customizace artefaktů
│   ├── chat_page/                   — chat se zdrojovým grounding
│   ├── infographic_artifact_page/   — infografika
│   ├── oauth_consent_page/          — OAuth souhlas
│   ├── player_page/                 — přehrávač audio/video
│   ├── project_list/                — seznam notebooků
│   ├── project_page/                — detail notebooku
│   ├── push_messaging_page/         — push notifikace
│   ├── query_to_notebook_page/      — dotaz → notebook konverze
│   ├── share_receiver_page/         — příjem sdíleného obsahu
│   ├── slides_artifact_editing_page/ — editace slides
│   ├── slides_artifact_page/        — zobrazení slides
│   ├── source_discovery_page/       — discovery zdrojů
│   ├── source_selection_page/       — výběr zdrojů
│   ├── source_viewer/               — prohlížeč zdrojů
│   ├── studio_page/                 — Studio tab (generování)
│   └── welcome_page/                — uvítací stránka
├── router/             — routing
├── services/
│   ├── artifacts/      — artifact service
│   ├── permission_handler/ — oprávnění
│   ├── persistence_proto_util/ — persistence
│   ├── push_messaging_service/ — push notifikace
│   ├── rpc/            — gRPC klient
│   ├── source/         — source service
│   ├── source_discovery/ — discovery service
│   ├── streaming_artifacts/ — streaming artefaktů
│   └── throttler/      — throttling
├── ui/
│   ├── audio_waveform_animation/   — animace zvukových vln
│   ├── colors/                     — barvy
│   ├── components/                 — sdílené komponenty
│   ├── cycling_widget_animator/    — cyklická animace
│   ├── gradient_text/              — gradientový text
│   ├── network_image/              — síťové obrázky
│   ├── position_slider/            — posuvník pozice
│   ├── spacing/                    — mezery
│   ├── spinning_border_card/       — karta s rotujícím okrajem
│   ├── tailwind_doc_renderer/      — renderer dokumentů
│   ├── typography/                 — typografie
│   └── video_player_overlay/       — overlay video přehrávače
└── watch_next/         — "Watch Next" doporučení
```

## Mapování na naši app (RpcMethod.kt)

### Již implementované (18 metod → nyní 62)
| Naše jméno | RPC ID | gRPC metoda | Stav |
|------------|--------|-------------|------|
| LIST_NOTEBOOKS | `wXbhsf` | ListRecentlyViewedProjects | ✅ implementováno |
| CREATE_NOTEBOOK | `CCqFvf` | CreateProject | ✅ implementováno |
| GET_NOTEBOOK | `rLM1Ne` | GetProject | ✅ implementováno |
| RENAME_NOTEBOOK | `s0tc2d` | MutateProject | ✅ implementováno |
| DELETE_NOTEBOOK | `WWINqb` | DeleteProjects | ✅ implementováno |
| ADD_SOURCE | `izAoDd` | AddSources | ✅ implementováno |
| DELETE_SOURCE | `tGMBJ` | DeleteSources | ✅ implementováno |
| GET_SOURCE | `hizoJc` | LoadSource | ✅ implementováno |
| SUMMARIZE | `VfAZjd` | GenerateNotebookGuide | ✅ implementováno |
| LIST_ARTIFACTS | `gArtLc` | ListArtifacts | ✅ implementováno |
| CREATE_NOTE | `CYK0Xb` | CreateNote | ✅ implementováno |
| UPDATE_NOTE | `cYAfTb` | MutateNote | ✅ implementováno |
| DELETE_NOTE | `AH0mwd` | DeleteNotes | ✅ implementováno |
| GET_NOTES | `cFji9` | GetNotes | ✅ implementováno |
| DELETE_ARTIFACT | `V5N4be` | DeleteArtifact | ✅ implementováno |
| CREATE_ARTIFACT | `R7cb6c` | CreateArtifact | ✅ implementováno |
| GET_ARTIFACT | `v9rmvd` | GetArtifact | ✅ implementováno |
| GET_CONVERSATION_TURNS | `khqZz` | ListChatTurns | ✅ implementováno |

### Nově objevené — high priority
| RPC ID | gRPC metoda | Proč je důležitá |
|--------|-------------|------------------|
| `laWbsf` | **GenerateFreeFormStreamed** | Streaming chat — hlavní chatovací endpoint |
| `Rytqqe` | **GenerateArtifact** | AI generování artefaktů (Studio tab) |
| `KmcKPe` | **DeriveArtifact** | Odvození nového artefaktu z existujícího |
| `rc3d8d` | **UpdateArtifact** | Editace artefaktu |
| `sqTeoe` | **GetArtifactCustomizationChoices** | Dostupné customizace pro artefakty |
| `hPTbtc` | **ListChatSessions** | Seznam chat sessions |
| `J7Gthc` | **DeleteChatTurns** | Mazání chatových zpráv |
| `te3DCe` | **CopyProject** | Kopírování notebooku |
| `b7Wfje` | **MutateSource** | Úprava zdroje (přejmenování atd.) |
| `FLmJqe` | **RefreshSource** | Obnovení zdroje z URL |
| `tr032e` | **GenerateDocumentGuides** | AI průvodce dokumentem |
| `QDyure` | **ShareProject** | Sdílení notebooku |
| `Krh3pd` | **ExportToDrive** | Export do Google Drive |

### Nově objevené — medium priority
| RPC ID | gRPC metoda | Proč |
|--------|-------------|------|
| `Es3dTe` | DiscoverSources | Auto-discovery zdrojů |
| `otmP3b` | GeneratePromptSuggestions | AI návrhy promptů pro chat |
| `ciyUvf` | GenerateReportSuggestions | AI návrhy reportů |
| `likKIe` | ExecuteWritingFunction | Psací funkce |
| `uK8f7c` | GenerateMagicView | Magic view |
| `EnujNd` | ListModelOptions | Seznam AI modelů |
| `yR9Yof` | CheckSourceFreshness | Kontrola aktuálnosti zdrojů |
| `yyryJe` | ActOnSources | Batch akce nad zdroji |

## Metodologie extrakce

### 1. Flutter APK (mobilní app)
```bash
# Stažení APK z telefonu
adb shell pm path com.google.android.apps.labs.language.tailwind
adb pull /data/app/.../base.apk
adb pull /data/app/.../split_config.arm64_v8a.apk

# Extrakce stringů z Dart AOT snapshot (17MB)
strings libNotebookLM_prod_android_library_flutter_artifacts.so > strings.txt
# → 40,256 stringů → filtrování regex patterny → gRPC metody, enumy, typy

# Java decompile přes JADX
jadx base.apk -d jadx-out/
# → SSOAuthPlugin.java, AgentCommsWebRtcSession.java atd.
```

### 2. Web JS bundle
```bash
# Stažení hlavního JS bundlu (veřejný, nepotřebuje auth)
curl -sL "https://www.gstatic.com/_/mss/boq-labs-tailwind/_/js/k=boq-labs-tailwind.LabsTailwindUi.cs.VP9SH7_wCvQ.es6.O/d=1/excm=_b/ed=1/dg=0/br=1/wt=2/ujg=1/rs=.../m=_b" > bundle.js
# → 2.5MB minifikovaný JS

# Extrakce RPC definic
grep -oP 'new _\.Tz\("[^"]+",\s*[^,]+,\s*\[[^\]]*?"/[^"]*"[^\]]*\]' bundle.js
# → 62 RPC metod s IDčky a service.method names
```

### 3. Pattern v JS bundlu
```javascript
// Každé RPC je registrované jako:
new _.Tz("RPC_ID", ResponseProtoClass, [
    _.Mz, true/false,     // true = GET, false = POST
    _.Oz, "/ServiceName.MethodName"
])
```

## Klíčové features které naše app nemá
1. **Studio tab** — generování 11 typů artefaktů (audio, video, flashcards, quiz, slides, infographic, report, app, table, mindmap, fantasy map)
2. **Source Discovery** — automatické objevení relevantních zdrojů
3. **Source Viewer** — prohlížení obsahu zdrojů s AI průvodcem (SourceGuide)
4. **Slides Editor** — editace generovaných prezentací
5. **Live Audio (Interactive)** — živý audio chat přes WebRTC
6. **Share Receiver** — příjem sdíleného obsahu z jiných apps
7. **Query to Notebook** — vytvoření notebooku z dotazu
8. **Watch Next** — doporučení dalšího obsahu
9. **Notebook Access Management** — sdílení notebooků (owner/writer/reader)
10. **Push Notifications** — push notifikace
11. **Deep Research** — hloubkový výzkum
12. **OAuth Consent** — OAuth flows pro Google služby
