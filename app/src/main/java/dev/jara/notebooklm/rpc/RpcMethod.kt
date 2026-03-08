package dev.jara.notebooklm.rpc

/**
 * RPC method IDs pro NotebookLM batchexecute API.
 * Reverse-engineered z notebooklm-py.
 */
enum class RpcMethod(val id: String) {
    LIST_NOTEBOOKS("wXbhsf"),
    CREATE_NOTEBOOK("CCqFvf"),
    GET_NOTEBOOK("rLM1Ne"),
    RENAME_NOTEBOOK("s0tc2d"),
    DELETE_NOTEBOOK("WWINqb"),
    ADD_SOURCE("izAoDd"),
    DELETE_SOURCE("tGMBJ"),
    GET_SOURCE("hizoJc"),
    SUMMARIZE("VfAZjd"),
    LIST_ARTIFACTS("gArtLc"),
    CREATE_NOTE("CYK0Xb"),
    UPDATE_NOTE("cYAfTb"),
    DELETE_NOTE("AH0mwd"),
    GET_NOTES("cFji9"),
    GENERATE_ARTIFACT("R7cb6c"),
    GET_INTERACTIVE_HTML("v9rmvd"),
    GET_CONVERSATION_TURNS("khqZz"),
}
