package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.preference.PreferenceDataStore
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.matsuri.MatsuriBean
import io.nekohasekai.sagernet.fmt.matsuri.onSharedStorageSet
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.plugin.MatsuriJSInterface
import io.nekohasekai.sagernet.plugin.MatsuriPreferenceInflater

class MatsuriSettingsActivity : ProfileSettingsActivity<MatsuriBean>() {

    lateinit var jsi: MatsuriJSInterface
    lateinit var jsip: MatsuriJSInterface.NekoProtocol
    lateinit var plgId: String
    lateinit var protocolId: String
    var loaded = false

    override fun createEntity() =
        MatsuriBean()

    override fun MatsuriBean.init() {
        if (!this@MatsuriSettingsActivity::plgId.isInitialized) this@MatsuriSettingsActivity.plgId = plgId
        if (!this@MatsuriSettingsActivity::protocolId.isInitialized) this@MatsuriSettingsActivity.protocolId =
            protocolId
        DataStore.profileCacheStore.putString("name", name)
        DataStore.matsuriPluginStorage = sharedStorage.toString()
    }

    override fun MatsuriBean.serialize() {
        // NekoBean from input
        plgId = this@MatsuriSettingsActivity.plgId
        protocolId = this@MatsuriSettingsActivity.protocolId

        sharedStorage = MatsuriBean.tryParseJSON(DataStore.matsuriPluginStorage)
        onSharedStorageSet()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        intent?.getStringExtra("plgId")?.apply { plgId = this }
        intent?.getStringExtra("protocolId")?.apply { protocolId = this }
        super.onCreate(savedInstanceState)
    }

    override fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
        listView.isVisible = false
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (loaded && key != Key.PROFILE_DIRTY) {
            dirty = true
            onBackPressedCallback.isEnabled = true
        }
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.matsuri_preferences)

        // Create a jsi
        jsi = MatsuriJSInterface(plgId)
        runOnIoDispatcher {
            try {
                jsi.init()
                jsip = jsi.switchProtocol(protocolId)
                jsi.jsObject.preferenceScreen = preferenceScreen

                // Because of the Preference problem, first require the KV and then inflate the UI
                jsip.setSharedStorage(DataStore.matsuriPluginStorage ?: "{}")
                jsip.requireSetProfileCache()

                val config = jsip.requirePreferenceScreenConfig()
                val pref = JsonParser.parseString(config).asJsonArray

                MatsuriPreferenceInflater.inflate(pref, preferenceScreen)
                jsip.onPreferenceCreated()

                runOnUiThread {
                    loaded = true
                    listView.isVisible = true
                }
            } catch (e: Exception) {
                //Dialogs.logExceptionAndShow(this@NekoSettingActivity, e) { finish() }
            }
        }
    }

    override suspend fun saveAndExit() {
        DataStore.matsuriPluginStorage = jsip.sharedStorageFromProfileCache()
        super.saveAndExit() // serialize & finish
    }

    override fun onDestroy() {
        if (this::jsi.isInitialized) jsi.destroy()
        super.onDestroy()
    }

}
