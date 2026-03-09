package dev.jara.notebooklm.rpc

/**
 * RPC method IDs pro NotebookLM batchexecute API.
 * Extrahováno z JS bundlu boq-labs-tailwind.LabsTailwindUi (_.Tz registrace).
 * Pattern: new _.Tz("RPC_ID", ResponseClass, [_.Mz, isGET, _.Oz, "/Service.Method"])
 */
enum class RpcMethod(val id: String) {
    // ── Projekty ──
    CREATE_NOTEBOOK("CCqFvf"),          // CreateProject
    GET_NOTEBOOK("rLM1Ne"),             // GetProject
    RENAME_NOTEBOOK("s0tc2d"),          // MutateProject
    DELETE_NOTEBOOK("WWINqb"),          // DeleteProjects
    LIST_NOTEBOOKS("wXbhsf"),          // ListRecentlyViewedProjects
    REMOVE_RECENTLY_VIEWED("fejl7e"),   // RemoveRecentlyViewedProject
    COPY_NOTEBOOK("te3DCe"),            // CopyProject
    GET_NOTEBOOK_ANALYTICS("AUrzMb"),   // GetProjectAnalytics
    LIST_FEATURED("ub2Bae"),            // ListFeaturedProjects
    UPDATE_FEATURED_STATUS("DemIHe"),   // UpdateFeaturedNotebookStatus

    // ── Zdroje ──
    ADD_SOURCE("izAoDd"),               // AddSources
    DELETE_SOURCE("tGMBJ"),             // DeleteSources
    GET_SOURCE("hizoJc"),               // LoadSource
    MUTATE_SOURCE("b7Wfje"),            // MutateSource
    REFRESH_SOURCE("FLmJqe"),           // RefreshSource
    ACT_ON_SOURCES("yyryJe"),           // ActOnSources
    CHECK_SOURCE_FRESHNESS("yR9Yof"),   // CheckSourceFreshness
    ADD_TENTATIVE_SOURCES("o4cbdc"),    // AddTentativeSources

    // ── Source Discovery ──
    DISCOVER_SOURCES("Es3dTe"),              // DiscoverSources
    DISCOVER_SOURCES_ASYNC("QA9ei"),         // DiscoverSourcesAsync
    DISCOVER_SOURCES_MANIFOLD("Ljjv0c"),     // DiscoverSourcesManifold
    LIST_DISCOVER_SOURCES_JOB("e3bVqc"),     // ListDiscoverSourcesJob
    FINISH_DISCOVER_SOURCES("LBwxtb"),       // FinishDiscoverSourcesRun
    CANCEL_DISCOVER_SOURCES("Zbrupe"),       // CancelDiscoverSourcesJob

    // ── Chat ──
    CHAT_STREAMED("laWbsf"),            // GenerateFreeFormStreamed (hlavní streaming chat)
    LIST_CHAT_SESSIONS("hPTbtc"),       // ListChatSessions
    GET_CONVERSATION_TURNS("khqZz"),    // ListChatTurns
    DELETE_CHAT_TURNS("J7Gthc"),        // DeleteChatTurns

    // ── Artefakty ──
    CREATE_ARTIFACT("R7cb6c"),          // CreateArtifact
    GENERATE_ARTIFACT("Rytqqe"),        // GenerateArtifact
    GET_ARTIFACT("v9rmvd"),             // GetArtifact
    LIST_ARTIFACTS("gArtLc"),           // ListArtifacts
    UPDATE_ARTIFACT("rc3d8d"),          // UpdateArtifact
    DELETE_ARTIFACT("V5N4be"),          // DeleteArtifact
    DERIVE_ARTIFACT("KmcKPe"),          // DeriveArtifact
    GET_ARTIFACT_CUSTOMIZATION("sqTeoe"), // GetArtifactCustomizationChoices
    GET_ARTIFACT_USER_STATE("ulBSjf"),  // GetArtifactUserState
    UPSERT_ARTIFACT_USER_STATE("Fxmvse"), // UpsertArtifactUserState

    // ── Poznámky & Průvodce ──
    GET_NOTES("cFji9"),                 // GetNotes
    CREATE_NOTE("CYK0Xb"),             // CreateNote
    UPDATE_NOTE("cYAfTb"),             // MutateNote
    DELETE_NOTE("AH0mwd"),             // DeleteNotes
    SUMMARIZE("VfAZjd"),               // GenerateNotebookGuide
    GENERATE_DOC_GUIDES("tr032e"),     // GenerateDocumentGuides

    // ── AI generování ──
    GENERATE_PROMPT_SUGGESTIONS("otmP3b"),  // GeneratePromptSuggestions
    GENERATE_REPORT_SUGGESTIONS("ciyUvf"),  // GenerateReportSuggestions
    EXECUTE_WRITING_FUNCTION("likKIe"),     // ExecuteWritingFunction
    GENERATE_MAGIC_VIEW("uK8f7c"),          // GenerateMagicView
    GET_MAGIC_VIEW("rtY7md"),               // GetMagicView
    GET_MAGIC_INDEX("XpqOp"),               // GetMagicIndex
    LIST_MODEL_OPTIONS("EnujNd"),           // ListModelOptions

    // ── Účet ──
    GET_OR_CREATE_ACCOUNT("ZwVcOc"),   // GetOrCreateAccount
    MUTATE_ACCOUNT("hT54vc"),          // MutateAccount
    GENERATE_ACCESS_TOKEN("preRPe"),   // GenerateAccessToken

    // ── Sdílení ──
    SHARE_PROJECT("QDyure"),            // ShareProject (SharingService)
    GET_SHARED_PROJECT_DETAILS("JFMDGd"), // GetProjectDetails (SharingService)
    CREATE_ACCESS_REQUEST("n3dkHd"),    // CreateAccessRequest (SharingService)

    // ── Export & Feedback ──
    EXPORT_TO_DRIVE("Krh3pd"),         // ExportToDrive
    SUBMIT_FEEDBACK("uNyJKe"),         // SubmitFeedback
    REPORT_CONTENT("OmVMXc"),          // ReportContent
}
