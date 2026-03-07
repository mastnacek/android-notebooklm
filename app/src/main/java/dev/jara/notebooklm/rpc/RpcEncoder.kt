package dev.jara.notebooklm.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.net.URLEncoder

/**
 * Encoder pro Google batchexecute RPC protokol.
 *
 * Format: f.req=[[[rpc_id, params_json, null, "generic"]]]&at=csrf_token&
 */
object RpcEncoder {

    private val json = Json { encodeDefaults = true }

    fun buildRequestBody(method: RpcMethod, params: JsonArray, csrfToken: String): String {
        val paramsJson = json.encodeToString(JsonArray.serializer(), params)

        // Vnitrni request: [rpc_id, params_json, null, "generic"]
        val inner = buildJsonArray {
            add(JsonPrimitive(method.id))
            add(JsonPrimitive(paramsJson))
            add(JsonNull)
            add(JsonPrimitive("generic"))
        }

        // Trojite zanoreni: [[[inner]]]
        val fReq = buildJsonArray {
            add(buildJsonArray {
                add(inner)
            })
        }

        val fReqJson = json.encodeToString(JsonArray.serializer(), fReq)
        val encodedReq = URLEncoder.encode(fReqJson, "UTF-8")
        val encodedToken = URLEncoder.encode(csrfToken, "UTF-8")

        return "f.req=$encodedReq&at=$encodedToken&"
    }

    fun buildUrl(method: RpcMethod, sessionId: String, sourcePath: String = "/"): String {
        val params = mapOf(
            "rpcids" to method.id,
            "source-path" to sourcePath,
            "f.sid" to sessionId,
            "hl" to "en",
            "rt" to "c",
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$BATCHEXECUTE_URL?$query"
    }

    private const val BATCHEXECUTE_URL =
        "https://notebooklm.google.com/_/LabsTailwindUi/data/batchexecute"
}
