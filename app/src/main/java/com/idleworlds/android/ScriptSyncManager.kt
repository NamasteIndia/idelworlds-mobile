package com.idleworlds.android

import android.util.Log
import at.pardus.android.webview.gm.model.Script
import at.pardus.android.webview.gm.model.ScriptId
import at.pardus.android.webview.gm.store.ScriptStoreSQLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges UserScriptManager (file-based source of truth) with ScriptStoreSQLite (execution engine).
 */
class ScriptSyncManager(
    private val userScriptManager: UserScriptManager,
    private val scriptStore: ScriptStoreSQLite
) {
    companion object {
        private const val TAG = "ScriptSyncManager"
        private const val DEFAULT_NAMESPACE = "com.idleworlds.android"
        private const val GAME_MATCH = "*://*.idleworlds.com/*"
    }

    suspend fun syncScripts() = withContext(Dispatchers.IO) {
        try {
            val allScripts = userScriptManager.getAllScripts()
            val syncedScriptIds = mutableSetOf<Pair<String, String>>()

            for (scriptInfo in allScripts) {
                val content = userScriptManager.loadScriptContent(scriptInfo.filename)
                if (content == null) {
                    Log.w(TAG, "Could not load content for ${scriptInfo.filename}, skipping")
                    continue
                }

                if (!scriptInfo.isEnabled) {
                    tryDeleteScript(scriptInfo.name, content, scriptInfo.url)
                    continue
                }

                val parsedScript = try {
                    Script.parse(content, scriptInfo.url.ifEmpty { null })
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing script ${scriptInfo.name}", e)
                    null
                }

                if (parsedScript != null) {
                    Log.d(TAG, "Syncing script with metadata: ${parsedScript.name} (namespace: ${parsedScript.namespace})")
                    scriptStore.add(parsedScript)
                    scriptStore.enable(ScriptId(parsedScript.name, parsedScript.namespace))
                    syncedScriptIds.add(Pair(parsedScript.name, parsedScript.namespace))
                } else {
                    Log.d(TAG, "Syncing raw script (no metadata): ${scriptInfo.name}")
                    val syntheticScript = Script(
                        scriptInfo.name,
                        DEFAULT_NAMESPACE,
                        null,
                        null,
                        arrayOf(GAME_MATCH),
                        null,
                        scriptInfo.url.ifEmpty { null },
                        null,
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        null,
                        content
                    )
                    scriptStore.add(syntheticScript)
                    scriptStore.enable(ScriptId(scriptInfo.name, DEFAULT_NAMESPACE))
                    syncedScriptIds.add(Pair(scriptInfo.name, DEFAULT_NAMESPACE))
                }
            }

            val storedScripts = scriptStore.all
            if (storedScripts != null) {
                for (stored in storedScripts) {
                    val key = Pair(stored.name, stored.namespace)
                    if (key !in syncedScriptIds) {
                        Log.d(TAG, "Removing orphaned script from store: ${stored.name} (${stored.namespace})")
                        scriptStore.delete(ScriptId(stored.name, stored.namespace))
                    }
                }
            }

            Log.d(TAG, "Script sync completed. ${syncedScriptIds.size} scripts synced.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during script sync", e)
        }
    }

    private fun tryDeleteScript(configName: String, content: String, url: String) {
        scriptStore.delete(ScriptId(configName, DEFAULT_NAMESPACE))

        try {
            val parsed = Script.parse(content, url.ifEmpty { null })
            if (parsed != null) {
                scriptStore.delete(ScriptId(parsed.name, parsed.namespace))
            }
        } catch (_: Exception) {
        }
    }
}
