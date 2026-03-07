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
}
