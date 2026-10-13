package com.idanck.nexplorer.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.idanck.nexplorer.core.ViewOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

enum class ThemeMode(val label: String) { System("מערכת"), Light("בהיר"), Dark("כהה") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val defaultView: ViewOptions = ViewOptions(),
    /** "רק בתיקייה זו": נתיב → אפשרויות. */
    val folderViews: Map<String, ViewOptions> = emptyMap(),
    val favorites: List<String> = emptyList(),
    val confirmDelete: Boolean = true,
    val reopenLastFolder: Boolean = true,
    val lastFolder: String? = null,
    val updateAutoCheck: Boolean = true,
    val updateWifiOnly: Boolean = false,
    val updateLastCheckUtc: Long = 0,
    val updateSkippedVersionCode: Int = 0,
    val updateLastManifest: String? = null,
) {
    fun viewFor(path: String?): ViewOptions = path?.let { folderViews[it] } ?: defaultView
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * ההעדפות. נקראות פעם אחת בסינכרון בהפעלה (קובץ קטן), כדי שהמסך הראשון יצויר
 * כבר בערכה ובתצוגה הנכונות במקום להבהב מברירת המחדל.
 */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.applicationContext.dataStore

    val state: StateFlow<AppSettings> = store.data
        .map(::read)
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { read(store.data.first()) })

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            prefs[THEME] = next.theme.name
            prefs[DEFAULT_VIEW] = next.defaultView.encode()
            prefs[FOLDER_VIEWS] = next.folderViews.entries.joinToString("\n") { (path, view) ->
                "$path\t${view.encode()}"
            }
            prefs[FAVORITES] = next.favorites.joinToString("\n")
            prefs[CONFIRM_DELETE] = next.confirmDelete
            prefs[REOPEN_LAST] = next.reopenLastFolder
            next.lastFolder?.let { prefs[LAST_FOLDER] = it } ?: prefs.remove(LAST_FOLDER)
            prefs[UPDATE_AUTO] = next.updateAutoCheck
            prefs[UPDATE_WIFI] = next.updateWifiOnly
            prefs[UPDATE_LAST_CHECK] = next.updateLastCheckUtc
            prefs[UPDATE_SKIPPED] = next.updateSkippedVersionCode
            next.updateLastManifest?.let { prefs[UPDATE_MANIFEST] = it } ?: prefs.remove(UPDATE_MANIFEST)
        }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.theme,
            defaultView = ViewOptions.decode(prefs[DEFAULT_VIEW]),
            folderViews = prefs[FOLDER_VIEWS].orEmpty().lineSequence()
                .mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) null else line.substring(0, tab) to ViewOptions.decode(line.substring(tab + 1))
                }
                .toMap(),
            favorites = prefs[FAVORITES].orEmpty().lines().filter { it.isNotBlank() },
            confirmDelete = prefs[CONFIRM_DELETE] ?: defaults.confirmDelete,
            reopenLastFolder = prefs[REOPEN_LAST] ?: defaults.reopenLastFolder,
            lastFolder = prefs[LAST_FOLDER],
            updateAutoCheck = prefs[UPDATE_AUTO] ?: defaults.updateAutoCheck,
            updateWifiOnly = prefs[UPDATE_WIFI] ?: defaults.updateWifiOnly,
            updateLastCheckUtc = prefs[UPDATE_LAST_CHECK] ?: 0,
            updateSkippedVersionCode = prefs[UPDATE_SKIPPED] ?: 0,
            updateLastManifest = prefs[UPDATE_MANIFEST],
        )
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val FOLDER_VIEWS = stringPreferencesKey("folder_views")
        val FAVORITES = stringPreferencesKey("favorites")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val REOPEN_LAST = booleanPreferencesKey("reopen_last_folder")
        val LAST_FOLDER = stringPreferencesKey("last_folder")
        val UPDATE_AUTO = booleanPreferencesKey("update_auto_check")
        val UPDATE_WIFI = booleanPreferencesKey("update_wifi_only")
        val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check_utc")
        val UPDATE_SKIPPED = intPreferencesKey("update_skipped_version_code")
        val UPDATE_MANIFEST = stringPreferencesKey("update_last_manifest")
    }
}
