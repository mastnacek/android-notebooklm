package dev.jara.notebooklm.rpc

import android.util.Log
import kotlinx.serialization.json.*

// ── Artifacts API — extension functions na NotebookLmApi ──

private const val TAG = NotebookLmApi.TAG

/** Rust: api::list_artifacts — RPC gArtLc */
suspend fun NotebookLmApi.listArtifacts(notebookId: String): List<Artifact> {
    val params = buildJsonArray {
        add(buildJsonArray { add(JsonPrimitive(2)) })
        add(JsonPrimitive(notebookId))
        add(JsonPrimitive("NOT artifact.status = \"ARTIFACT_STATUS_SUGGESTED\""))
    }
    val result = rpcCall(
        RpcMethod.LIST_ARTIFACTS, params,
        sourcePath = "/notebook/$notebookId"
    ) ?: return emptyList()

    return parseArtifacts(result)
}

private fun parseArtifacts(data: JsonElement): List<Artifact> {
    val artifacts = mutableListOf<Artifact>()
    try {
        Log.i(TAG, "parseArtifacts raw: ${data.toString().take(500)}")
        val list = data.jsonArray.getOrNull(0)?.jsonArray ?: data.jsonArray
        for (art in list) {
            try {
                val arr = art.jsonArray
                val id = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                val title = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: "(bez nazvu)"
                val typeCode = arr.getOrNull(2)?.jsonPrimitive?.intOrNull ?: 0
                val statusCode = arr.getOrNull(4)?.jsonPrimitive?.intOrNull ?: 4
                val url = extractArtifactUrl(arr, typeCode)
                Log.i(TAG, "parseArtifact: id=${id.take(8)}, type=$typeCode, status=$statusCode, url=${url?.take(80)}")
                // Loguj strukturu pro debug
                if (typeCode == 1) {
                    Log.i(TAG, "parseArtifact AUDIO raw[6]: ${arr.getOrNull(6)?.toString()?.take(300)}")
                } else {
                    // Loguj cely artefakt pro non-audio typy (hledame kde je URL)
                    Log.i(TAG, "parseArtifact type=$typeCode raw: ${arr.toString().take(800)}")
                }
                artifacts.add(Artifact(id, title, ArtifactType.fromCode(typeCode), ArtifactStatus.fromCode(statusCode), url))
            } catch (e: Exception) {
                Log.w(TAG, "parseArtifacts: skip: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "parseArtifacts: ${e.message}")
    }
    return artifacts
}

/** Rust: api::delete_artifact — RPC V5N4be */
suspend fun NotebookLmApi.deleteArtifact(artifactId: String) {
    val params = buildJsonArray {
        add(buildJsonArray { add(JsonPrimitive(2)) })
        add(JsonPrimitive(artifactId))
    }
    rpcCall(RpcMethod.DELETE_ARTIFACT, params, sourcePath = "/")
}

/** Spusti generovani artefaktu */
suspend fun NotebookLmApi.generateArtifact(
    notebookId: String,
    sources: List<Source>,
    type: GenerateType,
    options: GenerateOptions = GenerateOptions(),
): String? {
    val sourcesTriple = buildJsonArray {
        for (s in sources) {
            add(buildJsonArray { add(buildJsonArray { add(JsonPrimitive(s.id)) }) })
        }
    }
    val sourcesDouble = buildJsonArray {
        for (s in sources) {
            add(buildJsonArray { add(JsonPrimitive(s.id)) })
        }
    }

    val inst = if (options.instructions != null) JsonPrimitive(options.instructions) else JsonNull

    // Kazdy typ ma svuj specificky payload tvar (z Python reference)
    val innerArray = when (type) {
        GenerateType.AUDIO -> buildJsonArray {
            add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
            add(JsonNull); add(JsonNull)
            add(buildJsonArray {
                add(JsonNull)
                add(buildJsonArray {
                    add(inst)
                    add(JsonPrimitive(options.audioLength.code))
                    add(JsonNull)
                    add(sourcesDouble)
                    add(JsonPrimitive(options.language))
                    add(JsonNull)
                    add(JsonPrimitive(options.audioFormat.code))
                })
            })
        }
        GenerateType.VIDEO -> buildJsonArray {
            add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
            add(JsonNull); add(JsonNull)
            add(buildJsonArray {
                add(JsonNull); add(JsonNull); add(JsonNull)
                add(buildJsonArray {
                    add(inst); add(JsonNull); add(sourcesDouble)
                    add(JsonPrimitive(options.language)); add(JsonNull)
                    add(JsonPrimitive(options.videoFormat.code))
                    add(JsonPrimitive(options.videoStyle.code))
                })
            })
        }
        GenerateType.QUIZ -> buildJsonArray {
            add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
            add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
            add(buildJsonArray {
                add(JsonNull)
                add(buildJsonArray {
                    add(JsonPrimitive(2)) // variant: quiz
                    add(JsonNull); add(inst); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                    add(buildJsonArray {
                        add(JsonPrimitive(options.quizQuantity.code))
                        add(JsonPrimitive(options.quizDifficulty.code))
                    })
                })
            })
        }
        GenerateType.INFOGRAPHIC -> buildJsonArray {
            add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
            // Padding do pozice [14]
            repeat(10) { add(JsonNull) }
            add(buildJsonArray {
                add(buildJsonArray {
                    add(inst)
                    add(JsonPrimitive(options.language))
                    add(JsonNull)
                    add(JsonPrimitive(options.infographicOrientation.code))
                    add(JsonPrimitive(options.infographicDetail.code))
                })
            })
        }
        GenerateType.SLIDE_DECK -> buildJsonArray {
            add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
            // Padding do pozice [16]
            repeat(12) { add(JsonNull) }
            add(buildJsonArray {
                add(buildJsonArray {
                    add(inst)
                    add(JsonPrimitive(options.language))
                    add(JsonPrimitive(options.slideDeckFormat.code))
                    add(JsonPrimitive(options.slideDeckLength.code))
                })
            })
        }
        else -> buildJsonArray {
            add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
        }
    }

    val params = buildJsonArray {
        add(buildJsonArray { add(JsonPrimitive(2)) })
        add(JsonPrimitive(notebookId))
        add(innerArray)
    }

    val result = rpcCall(RpcMethod.CREATE_ARTIFACT, params, sourcePath = "/notebook/$notebookId")
    return try {
        result?.jsonArray?.getOrNull(0)?.jsonArray
            ?.getOrNull(0)?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) { null }
}

/** Ziska interaktivni HTML kvizu nebo flashcards */
suspend fun NotebookLmApi.getInteractiveHtml(notebookId: String, artifactId: String): String? {
    // Python reference: rpc_call(method, [artifact_id]) → params = ["id"]
    val params = buildJsonArray {
        add(JsonPrimitive(artifactId))
    }
    val result = rpcCall(RpcMethod.GET_ARTIFACT, params, sourcePath = "/notebook/$notebookId")
        ?: return null
    // Loguj celou odpoved po castech (logcat ma limit ~4000 znaku na radek)
    val fullRaw = result.toString()
    Log.i(TAG, "getInteractiveHtml raw length: ${fullRaw.length}")
    fullRaw.chunked(3000).forEachIndexed { i, chunk ->
        Log.i(TAG, "getInteractiveHtml raw[$i]: $chunk")
    }
    return try {
        // Python: result[0][9][0] = HTML content
        val data = result.jsonArray[0].jsonArray
        val html = data.getOrNull(9)?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull
        html ?: findFirstString(result)
    } catch (_: Exception) {
        try { findFirstString(result) } catch (_: Exception) { null }
    }
}

/** Extrahuje URL z artifact dat */
private fun extractArtifactUrl(arr: JsonArray, typeCode: Int): String? {
    return try {
        when (typeCode) {
            // Audio: hledame URL v art[6] — je to pole kde stringy jsou URLs
            // Format: [null, [...], "url_m140", "url_m18", ...]
            // Preferujeme URL bez =m140 suffix (lepsi kompatibilita), nebo prvni URL
            1 -> {
                val inner = arr.getOrNull(6)?.jsonArray ?: return null
                // Najdi vsechny URL stringy
                val urls = inner.mapNotNull { el ->
                    try { el.jsonPrimitive.contentOrNull }
                    catch (_: Exception) { null }
                }.filter { it.startsWith("https://") }
                Log.i(TAG, "extractArtifactUrl AUDIO: found ${urls.size} urls")
                urls.forEach { Log.i(TAG, "  url: ${it.takeLast(30)}") }
                // Preferuj URL s =m18 (MP4/AAC), pak =m140 (WebM), pak cokoliv
                urls.firstOrNull { it.contains("=m18") }
                    ?: urls.firstOrNull()
            }

            // Video: art[8] item s "video/mp4"
            3 -> arr.getOrNull(8)?.jsonArray?.firstNotNullOfOrNull { item ->
                try {
                    val mime = item.jsonArray.getOrNull(2)?.jsonPrimitive?.contentOrNull
                    if (mime == "video/mp4") item.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull
                    else null
                } catch (_: Exception) { null }
            }

            // Infographic: hledame URL v hluboke strukture
            7 -> arr.toList().asReversed().firstNotNullOfOrNull { item ->
                try {
                    item.jsonArray.getOrNull(2)?.jsonArray
                        ?.getOrNull(0)?.jsonArray
                        ?.getOrNull(1)?.jsonArray
                        ?.getOrNull(0)?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.startsWith("http") }
                } catch (_: Exception) { null }
            }

            // SlideDeck/prezentace: art[16][3] = PDF URL
            8 -> arr.getOrNull(16)?.jsonArray
                ?.getOrNull(3)?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.startsWith("http") }

            else -> null
        }
    } catch (_: Exception) { null }
}
