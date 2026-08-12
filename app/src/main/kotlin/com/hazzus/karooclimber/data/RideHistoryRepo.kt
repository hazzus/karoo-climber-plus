package com.hazzus.karooclimber.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Its own preferences file, kept separate from settings so ride state can be cleared independently. */
private val Context.rideHistoryDataStore by preferencesDataStore(name = "ride_history")

/**
 * DataStore-backed store for the per-ride completed-climbs list. The whole (small) list
 * lives under a single string key: one [CompletedClimb.encode] record per line, records
 * joined by `"\n"`. Reading splits on `"\n"`, drops blank lines and undecodable records.
 */
class RideHistoryRepo(private val context: Context) {

    private object Keys {
        val completed = stringPreferencesKey("completed_climbs")
    }

    val completed: Flow<List<CompletedClimb>> = context.rideHistoryDataStore.data.map { p ->
        p[Keys.completed].decodeList()
    }

    /** One-shot read for restore-on-start. */
    suspend fun load(): List<CompletedClimb> = completed.first()

    /** Overwrite the whole list (no append — the list is a handful of climbs). */
    suspend fun save(list: List<CompletedClimb>) {
        context.rideHistoryDataStore.edit { p ->
            p[Keys.completed] = list.joinToString("\n") { it.encode() }
        }
    }

    /** Remove the key so a fresh ride starts empty. */
    suspend fun clear() {
        context.rideHistoryDataStore.edit { p ->
            p.remove(Keys.completed)
        }
    }

    private fun String?.decodeList(): List<CompletedClimb> =
        this?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { decodeCompletedClimb(it) }
            ?: emptyList()
}
