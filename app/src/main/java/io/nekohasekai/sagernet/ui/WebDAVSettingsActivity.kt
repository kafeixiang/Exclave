package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import kotlin.io.encoding.Base64

class WebDAVSettingsActivity : ThemedActivity() {

    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.layout_webdav_settings)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.webdav_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, WebDAVSettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            // 处理底部导航栏高度，避免设置项被遮挡
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    left = bars.left,
                    right = bars.right,
                    bottom = bars.bottom
                )
                insets
            }
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

        private fun openWebDAVConn(urlString: String, method: String, credentials: String?): HttpURLConnection {
            val url = URL(urlString)
            val conn = if (SagerNet.started && DataStore.startedProfile > 0) {
                url.openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", DataStore.socksPort))) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            try {
                conn.requestMethod = method
            } catch (e: ProtocolException) {
                try {
                    val target = try {
                        val delegateField = conn.javaClass.getDeclaredField("delegate")
                        delegateField.isAccessible = true
                        delegateField.get(conn)
                    } catch (_: Exception) {
                        conn
                    }
                    val methodField = HttpURLConnection::class.java.getDeclaredField("method")
                    methodField.isAccessible = true
                    methodField.set(target, method)
                } catch (_: Exception) {
                }
            }
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (!credentials.isNullOrEmpty()) {
                conn.setRequestProperty("Authorization", credentials)
            }
            return conn
        }

        private fun testWebDAV() {
            runOnDefaultDispatcher {
                try {
                    val server = DataStore.webdavServer.trimEnd('/')
                    if (server.isBlank()) {
                        throw Exception(getString(R.string.webdav_server_empty))
                    }

                    if (!server.startsWith("http://", ignoreCase = true) && !server.startsWith("https://", ignoreCase = true)) {
                        throw Exception("Invalid server URL: must start with http:// or https://")
                    }

                    val credentials = "Basic " + Base64.encode(
                        "${DataStore.webdavUsername}:${DataStore.webdavPassword}".toByteArray()
                    )

                    val conn = openWebDAVConn(server, "PROPFIND", credentials)
                    conn.setRequestProperty("Depth", "0")
                    val code = conn.responseCode

                    when (code) {
                        401 -> throw Exception(getString(R.string.webdav_auth_error))
                        403 -> throw Exception(getString(R.string.webdav_permission_denied))
                        404 -> throw Exception(getString(R.string.webdav_server_not_found))
                        in 500..599 -> throw Exception(getString(R.string.webdav_server_error))
                    }

                    if (code != 200 && code != 207) {
                        throw Exception(getString(R.string.webdav_connect_failed, code))
                    }

                    val path = DataStore.webdavPath.trim('/')
                    if (path.isNotBlank()) {
                        var dirUrl = server
                        for (segment in path.split('/').filter { it.isNotEmpty() }) {
                            dirUrl += "/" + URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                        }

                        val dirConn = openWebDAVConn(dirUrl, "MKCOL", credentials)
                        val dirCode = dirConn.responseCode
                        if (dirCode != 200 && dirCode != 201 && dirCode != 405) {
                            throw Exception(getString(R.string.webdav_create_dir_failed))
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
