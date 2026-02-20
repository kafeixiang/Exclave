package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.widget.Toolbar
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

class WebDAVSettingsActivity : ThemedActivity() {

    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.layout_webdav_settings)
        toolbar = findViewById(R.id.toolbar)
        toolbar.setTitle(R.string.webdav_settings)
        toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, WebDAVSettingsFragment())
            .commit()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
    }

    class WebDAVSettingsFragment : PreferenceFragmentCompat() {
        private var lastClickTime = 0L
        private val DEBOUNCE_TIME = 1000L
        private var isFragmentAlive = true

        private fun isClickAllowed(): Boolean {
            val currentTime = System.currentTimeMillis()
            val isAllowed = currentTime - lastClickTime > DEBOUNCE_TIME
            if (isAllowed) {
                lastClickTime = currentTime
            }
            return isAllowed
        }

        override fun onDestroy() {
            isFragmentAlive = false
            super.onDestroy()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.configurationStore
            addPreferencesFromResource(R.xml.webdav_preferences)

            findPreference<EditTextPreference>("webdavServer")?.apply {
                setOnBindEditTextListener { editText ->
                    editText.setSingleLine()
                    editText.setSelection(editText.text.length)
                }
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }

            findPreference<EditTextPreference>("webdavUsername")?.apply {
                setOnBindEditTextListener { editText ->
                    editText.setSingleLine()
                    editText.setSelection(editText.text.length)
                }
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }

            findPreference<EditTextPreference>("webdavPassword")?.apply {
                setOnBindEditTextListener { editText ->
                    editText.setSingleLine()
                    editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    editText.setSelection(editText.text.length)
                }
                summaryProvider = ProfileSettingsActivity.PasswordSummaryProvider
            }

            findPreference<EditTextPreference>("webdavPath")?.apply {
                setOnBindEditTextListener { editText ->
                    editText.setSingleLine()
                    editText.setSelection(editText.text.length)
                }
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }

            findPreference<Preference>("webdavTest")?.setOnPreferenceClickListener {
                if (isClickAllowed()) {
                    testWebDAV()
                } else {
                    (requireActivity() as? WebDAVSettingsActivity)?.snackbar("请稍后再试")?.show()
                }
                true
            }
        }

        private fun testWebDAV() {
            runOnDefaultDispatcher {
                try {
                    val server = DataStore.webdavServer
                    if (server.isBlank()) {
                        throw Exception(getString(R.string.webdav_server_empty))
                    }

                    val url = try { URL(server) } catch (e: Exception) { throw Exception("Invalid URL") }
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build()

                    val authRequest = Request.Builder()
                        .url(url)
                        .method("PROPFIND", null)
                        .apply {
                            val credentials = Credentials.basic(
                                DataStore.webdavUsername,
                                DataStore.webdavPassword
                            )
                            header("Authorization", credentials)
                            header("Depth", "0")
                        }
                        .build()

                    val response = client.newCall(authRequest).execute()

                    when (response.code) {
                        401 -> throw Exception(getString(R.string.webdav_auth_error))
                        403 -> throw Exception(getString(R.string.webdav_permission_denied))
                        404 -> throw Exception(getString(R.string.webdav_server_not_found))
                        in 500..599 -> throw Exception(getString(R.string.webdav_server_error))
                    }

                    if (!response.isSuccessful) {
                        throw Exception(getString(R.string.webdav_connect_failed, response.code))
                    }

                    val path = DataStore.webdavPath.trim('/')
                    if (path.isNotBlank()) {
                        val baseHttpUrl = server.toHttpUrlOrNull()
                            ?: throw Exception(getString(R.string.webdav_server_not_found))

                        val dirUrl = baseHttpUrl.newBuilder().apply {
                            path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
                                addPathSegment(segment)
                            }
                        }.build()

                        val dirRequest = Request.Builder()
                            .url(dirUrl)
                            .method("MKCOL", null)
                            .apply {
                                val credentials = Credentials.basic(
                                    DataStore.webdavUsername,
                                    DataStore.webdavPassword
                                )
                                header("Authorization", credentials)
                            }
                            .build()

                        client.newCall(dirRequest).execute().use { dirResponse ->
                            if (!dirResponse.isSuccessful && dirResponse.code != 405) {
                                throw Exception(getString(R.string.webdav_create_dir_failed))
                            }
                        }
                    }

                    onMainDispatcher {
                        if (!isFragmentAlive) return@onMainDispatcher
                        (requireActivity() as? WebDAVSettingsActivity)?.snackbar(R.string.webdav_test_success)?.show()
                    }
                } catch (e: Exception) {
                    onMainDispatcher {
                        if (!isFragmentAlive) return@onMainDispatcher
                        val message = e.message ?: "Unknown error"
                        (requireActivity() as? WebDAVSettingsActivity)?.snackbar(getString(R.string.webdav_test_failed, message))?.show()
                    }
                }
            }
        }
    }
}
