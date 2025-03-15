package io.nekohasekai.sagernet.plugin

import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import cn.hutool.json.JSONArray
import cn.hutool.json.JSONObject
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UNCHECKED_CAST")
object MatsuriPreferenceInflater {
    suspend fun inflate(pref: JSONArray, preferenceScreen: PreferenceScreen) = withContext(Dispatchers.Main) {
        val context = preferenceScreen.context
        (pref as? List<JSONObject>)?.forEach { category ->
            val preferenceCategory = PreferenceCategory(context)
            preferenceScreen.addPreference(preferenceCategory)
            category.getStr("key")?.apply { preferenceCategory.key = this }
            category.getStr("title")?.apply { preferenceCategory.title = this }
            (category.getJSONArray("preferences") as? List<JSONObject>)?.forEach { preference ->
                lateinit var p: Preference
                when (preference.getStr("type")) {
                    "EditTextPreference" -> {
                        p = EditTextPreference(context).apply {
                            summaryProvider = when (preference.getStr("summaryProvider")) {
                                "PasswordSummaryProvider" -> ProfileSettingsActivity.PasswordSummaryProvider
                                else -> EditTextPreference.SimpleSummaryProvider.getInstance()
                            }
                            when (preference.getStr("EditTextPreferenceModifiers")) {
                                "Monospace" -> setOnBindEditTextListener(
                                    EditTextPreferenceModifiers.Monospace
                                )
                                "Hosts" -> setOnBindEditTextListener(
                                    EditTextPreferenceModifiers.Hosts
                                )
                                "Port" -> setOnBindEditTextListener(
                                    EditTextPreferenceModifiers.Port
                                )
                                "Number" -> setOnBindEditTextListener(
                                    EditTextPreferenceModifiers.Number
                                )
                            }
                        }
                    }
                    "SwitchPreference" -> {
                        p = SwitchPreference(context)
                    }
                    "SimpleMenuPreference" -> {
                        p = SimpleMenuPreference(context).apply {
                            preference.getJSONObject("entries")?.let {
                                setMenu(this, it)
                            }
                        }
                    }
                }
                p.key = preference.getStr("key")
                preference.getStr("title")?.apply { p.title = this }
                preference.getStr("summary")?.apply {
                    p.summary = this
                }
                preferenceCategory.addPreference(p)
            }
        }
    }
    fun setMenu(p: SimpleMenuPreference, entries: JSONObject) {
        val menuEntries = mutableListOf<String>()
        val menuEntryValues = mutableListOf<String>()
        entries.forEach { s, b ->
            menuEntryValues.add(s)
            menuEntries.add(b as String)
        }
        entries.apply {
            p.entries = menuEntries.toTypedArray()
            p.entryValues = menuEntryValues.toTypedArray()
        }
    }
}