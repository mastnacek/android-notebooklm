# NotebookLM Mobile App — Extrahované API (z Dart AOT snapshot)

Extrahováno z APK `com.google.android.apps.labs.language.tailwind` (Flutter app)
pomocí `strings` na `libNotebookLM_prod_android_library_flutter_artifacts.so` (Dart AOT snapshot).

## gRPC Server
- **Produkce:** `notebooklm-pa.googleapis.com`
- **Staging:** `staging-notebooklm-pa.sandbox.googleapis.com`
- **Autopush:** `autopush-notebooklm-pa.sandbox.googleapis.com`

## gRPC Service
`google.internal.labs.tailwind.orchestration.v1.LabsTailwindOrchestrationService`

## Kompletní seznam RPC metod (35 metod)

### Projekty (Notebooky)
| Metoda | Popis |
|--------|-------|
| CreateProject | Vytvoření nového notebooku |
| GetProject | Detail notebooku |
| MutateProject | Úprava notebooku (přejmenování, nastavení) |
| DeleteProjects | Smazání notebooků (batch) |
| ListRecentlyViewedProjects | Nedávno zobrazené notebooky |
| RemoveRecentlyViewedProject | Odebrání z nedávných |

### Zdroje (Sources)
| Metoda | Popis |
|--------|-------|
| AddSources | Přidání zdrojů do notebooku |
| DeleteSources | Smazání zdrojů |
| LoadSource | Načtení obsahu zdroje |
| ActOnSources | Akce nad zdroji (batch) |
| GetDriveSourceStatus | Status Google Drive zdroje |
| DiscoverSources | Objevení zdrojů (URL, web scraping) |
| FinishDiscoverSourcesRun | Dokončení discovery session |

### Chat
| Metoda | Popis |
|--------|-------|
| ListChatSessions | Seznam chat sessions |
| ListChatTurns | Historie chatu |
| DeleteChatTurns | Smazání chatových zpráv |

### Artefakty (Studio tab)
| Metoda | Popis |
|--------|-------|
| CreateArtifact | Vytvoření artefaktu |
| GetArtifact | Detail artefaktu |
| ListArtifacts | Seznam artefaktů |
| UpdateArtifact | Úprava artefaktu |
| DeleteArtifact | Smazání artefaktu |
| DeriveArtifact | Odvození nového artefaktu z existujícího |
| SuggestArtifacts | AI návrhy artefaktů |
| GetArtifactCustomizationChoices | Dostupné customizace |
| GetArtifactUserState | Uživatelský stav artefaktu |
| UpsertArtifactUserState | Uložení uživatelského stavu |

### Poznámky & Průvodce
| Metoda | Popis |
|--------|-------|
| GetNotes | Získání poznámek |
| GenerateDocumentGuides | AI průvodce dokumentem |
| GenerateNotebookGuide | AI průvodce notebookem |

### Účet & Auth
| Metoda | Popis |
|--------|-------|
| GetOrCreateAccount | Získání/vytvoření účtu |
| MutateAccount | Úprava nastavení účtu |
| GenerateAccessToken | Generování přístupového tokenu |

### Live Audio (WebRTC)
| Metoda | Popis |
|--------|-------|
| GetIceConfig | ICE konfigurace pro WebRTC |
| SendSdpOffer | Odeslání SDP nabídky pro WebRTC |

### Ostatní
| Metoda | Popis |
|--------|-------|
| SubmitFeedback | Odeslání zpětné vazby |
| ShareLink | Vytvoření sdíleného odkazu |

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
