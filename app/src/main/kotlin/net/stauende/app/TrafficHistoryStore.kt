package net.stauende.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists [TrafficAnalyzer] observations to app-private storage so the
 * "recurring jams" history actually survives across app launches, instead
 * of being rebuilt from scratch every time the process starts.
 *
 * Uses [org.json] (bundled with Android) rather than pulling in a JSON
 * library dependency for this simple nested-map shape.
 */
class TrafficHistoryStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    /** Loads the last-saved snapshot, or an empty map if none exists / it's unreadable. */
    fun load(): Map<String, Map<String, List<Int>>> {
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            val result = mutableMapOf<String, Map<String, List<Int>>>()
            root.keys().forEach { routeId ->
                val bucketsJson = root.getJSONObject(routeId)
                val buckets = mutableMapOf<String, List<Int>>()
                bucketsJson.keys().forEach { timeKey ->
                    val values = bucketsJson.getJSONArray(timeKey)
                    buckets[timeKey] = List(values.length()) { values.getInt(it) }
                }
                result[routeId] = buckets
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load traffic history, starting fresh", e)
            emptyMap()
        }
    }

    /** Writes [snapshot] (see [TrafficAnalyzer.snapshot]) to disk, overwriting any previous file. */
    fun save(snapshot: Map<String, Map<String, List<Int>>>) {
        try {
            val root = JSONObject()
            snapshot.forEach { (routeId, buckets) ->
                val bucketsJson = JSONObject()
                buckets.forEach { (timeKey, values) -> bucketsJson.put(timeKey, JSONArray(values)) }
                root.put(routeId, bucketsJson)
            }
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save traffic history", e)
        }
    }

    companion object {
        private const val TAG = "TrafficHistoryStore"
        private const val FILE_NAME = "traffic_history.json"
    }
}
