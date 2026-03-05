package io.nekohasekai.sagernet.plugin

import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UNCHECKED_CAST")
object MatsuriPreferenceInflater {
    suspend fun inflate(pref: JsonArray, preferenceScreen: PreferenceScreen) = withContext(Dispatchers.Main) {
        val context = preferenceScreen.context
        
        // Ensure root screen has a key
        if (preferenceScreen.key == null) {
            preferenceScreen.key = "matsuri_root_screen_${System.currentTimeMillis()}"
        }

        for (index in 0 until pref.size()) {
            val categoryJson = pref.get(index).asJsonObject ?: continue
            
            val preferenceCategory = PreferenceCategory(context)
            
            // Set key and disable persistence BEFORE adding to screen
            val categoryKey = categoryJson.getString("key") ?: "matsuri_cat_$index"
            preferenceCategory.key = categoryKey
            preferenceCategory.isPersistent = false 
            
            categoryJson.getString("title")?.let { preferenceCategory.title = it }
            
            preferenceScreen.addPreference(preferenceCategory)
            
            val preferences = categoryJson.getJsonArray("preferences") ?: continue
            for (pIndex in 0 until preferences.size()) {
                val preferenceJson = preferences.get(pIndex).asJsonObject ?: continue
                var p: Preference? = null
                
                when (preferenceJson.getString("type")) {
                    "EditTextPreference" -> {
                        p = EditTextPreference(context).apply {
                            summaryProvider = when (preferenceJson.getString("summaryProvider")) {
                                "PasswordSummaryProvider" -> ProfileSettingsActivity.PasswordSummaryProvider
                                else -> EditTextPreference.SimpleSummaryProvider.getInstance()
                            }
                            when (preferenceJson.getString("EditTextPreferenceModifiers")) {
                                "Monospace" -> setOnBindEditTextListener(EditTextPreferenceModifiers.Monospace)
                                "Hosts" -> setOnBindEditTextListener(EditTextPreferenceModifiers.Hosts)
                                "Port" -> setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
                                "Number" -> setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
                            }
                        }
                    }
                    "SwitchPreference" -> {
                        p = SwitchPreference(context)
                    }
                    "SimpleMenuPreference" -> {
                        p = SimpleMenuPreference(context).apply {
                            preferenceJson.getObject("entries")?.let {
                                setMenu(this, it)
                            }
                        }
                    }
                }
                
                p?.let { prefItem ->
                    val itemKey = preferenceJson.getString("key") ?: "${categoryKey}_item_$pIndex"
                    prefItem.key = itemKey
                    prefItem.isPersistent = false // Dynamic prefs should not persist to SharedPreferences

                    preferenceJson.getString("title")?.let { prefItem.title = it }
                    preferenceJson.getString("summary")?.let { prefItem.summary = it }
                    
                    preferenceCategory.addPreference(prefItem)
                }
            }
        }
    }
    
    fun setMenu(p: SimpleMenuPreference, entries: JsonObject) {
        val menuEntries = mutableListOf<String>()
        val menuEntryValues = mutableListOf<String>()
        entries.entrySet().forEach { (s, b) ->
            menuEntryValues.add(s)
            menuEntries.add(b.asString)
        }
        p.entries = menuEntries.toTypedArray()
        p.entryValues = menuEntryValues.toTypedArray()
    }
}
