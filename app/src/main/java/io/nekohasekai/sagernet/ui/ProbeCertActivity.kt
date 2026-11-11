/******************************************************************************
 *                                                                            *
 * Copyright (C) 2024  dyhkwong                                               *
 * Copyright (C) 2024  HystericalDragons                                      *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <https://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutProbeCertBinding
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import libexclavecore.Libexclavecore

class ProbeCertActivity : ThemedActivity() {

    private lateinit var binding: LayoutProbeCertBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutProbeCertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.probeCertCard.applyGlassBlur()
        binding.certificateCard.applyGlassBlur()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                bottom = ime.bottom,
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = if (ime.bottom > bars.bottom) 0 else bars.bottom - ime.bottom + dp2px(16),
            )
            insets
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.probe_cert)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        val initialProtocol = DataStore.certProberProtocol.coerceIn(0, 1)
        val initialHashType = DataStore.certProberHashType.coerceIn(0, 2)
        binding.probeCertServer.setText(DataStore.certProberServerAddress)
        binding.probeCertServerPort.setText(DataStore.certProberServerPort.toString())
        binding.probeCertServerName.setText(DataStore.certProberSNI)
        binding.probeCertProtocol.setSelection(initialProtocol)
        binding.probeCertAlpn.setText(DataStore.certProberALPN)
        binding.certHashType.setSelection(initialHashType)

        binding.probeCertServer.doAfterTextChanged { text ->
            binding.probeCertServerName.setText(text)
        }
        var lastProtocolPosition = initialProtocol
        binding.probeCertProtocol.post {
            binding.probeCertProtocol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    if (position == lastProtocolPosition) return
                    lastProtocolPosition = position
                    when (position) {
                        0 -> binding.probeCertAlpn.setText("h2,http/1.1")
                        1 -> binding.probeCertAlpn.setText("h3")
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
        }

        binding.probeCert.setOnClickListener {
            probeCert()
        }

        binding.certificate.doAfterTextChanged { text ->
            updateCertificateHashAndInfo(
                text?.toString().orEmpty(),
                binding.certHashType.selectedItemPosition
            )
        }
        binding.certHashType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                updateCertificateHashAndInfo(
                    binding.certificate.text?.toString().orEmpty(),
                    position
                )
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
        binding.certHash.setOnClickListener {
            val hash = binding.certHash.text?.toString().orEmpty()
            if (hash.isNotEmpty()) {
                val success = SagerNet.trySetPrimaryClip(hash)
                Snackbar.make(
                    binding.mainLayout,
                    if (success) R.string.copy_success else R.string.copy_failed,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        binding.showCertInfo.text = "?"
        binding.showCertInfo.isVisible = false
        binding.showCertInfo.setOnClickListener {
            showCertInfoDialog()
        }
    }

    private fun computeCertHash(certificate: String, hashType: Int): String {
        return when (hashType) {
            0 -> Libexclavecore.calculatePEMCertSHA256Hash(certificate)
            1 -> Libexclavecore.calculatePEMCertPublicKeySHA256Hash(certificate)
            2 -> Libexclavecore.calculatePEMCertChainSHA256Hash(certificate)
            else -> throw IllegalArgumentException("Unsupported hash type: $hashType")
        }
    }

    private fun updateCertificateHashAndInfo(certificate: String, hashType: Int) {
        if (certificate.isBlank()) {
            binding.certHash.text = ""
            binding.showCertInfo.isVisible = false
            return
        }
        try {
            binding.certHash.text = computeCertHash(certificate, hashType)
            binding.showCertInfo.isVisible = true
        } catch (_: Exception) {
            binding.certHash.text = ""
            binding.showCertInfo.isVisible = false
        }
    }

    private fun showCertInfoDialog() {
        try {
            val prettyInfo = Libexclavecore.certificateToPrettyInfo(
                binding.certificate.text?.toString().orEmpty()
            )
            if (isFinishing || isDestroyed) return
            val dialogView = NestedScrollView(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
                addView(HorizontalScrollView(this@ProbeCertActivity).apply {
                    addView(TextView(this@ProbeCertActivity).apply {
                        text = prettyInfo
                        isSingleLine = false
                        typeface = Typeface.MONOSPACE
                        setTextIsSelectable(true)
                        setHorizontallyScrolling(false)
                    })
                })
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.certificates)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .apply { applyGlassBlur() }
                .show()
        } catch (e: Exception) {
            showErrorDialog(e.readableMessage)
        }
    }

    private fun showErrorDialog(message: String) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .apply { applyGlassBlur() }
            .show()
    }

    private fun setLoading(loading: Boolean) {
        binding.waitLayout.isVisible = loading
        binding.mainLayout.isVisible = !loading
    }

    private fun probeCert() {
        val server = binding.probeCertServer.text?.toString()?.trim().orEmpty()
        if (server.isEmpty()) {
            setLoading(false)
            binding.certificate.setText("")
            showErrorDialog(getString(R.string.invalid_server))
            return
        }
        val port = binding.probeCertServerPort.text?.toString()?.trim()?.toIntOrNull()
        if (port == null || port !in 1..65535) {
            setLoading(false)
            binding.certificate.setText("")
            showErrorDialog(NumberFormatException("Invalid port: ${binding.probeCertServerPort.text}").toString())
            return
        }
        val serverName = binding.probeCertServerName.text?.toString()?.trim().orEmpty()
        val alpn = binding.probeCertAlpn.text?.toString()?.trim().orEmpty()
        val protocol = binding.probeCertProtocol.selectedItemPosition
        val hashType = binding.certHashType.selectedItemPosition

        DataStore.certProberServerAddress = server
        DataStore.certProberServerPort = port
        DataStore.certProberSNI = serverName
        DataStore.certProberALPN = alpn
        DataStore.certProberProtocol = protocol
        DataStore.certProberHashType = hashType

        setLoading(true)
        runOnDefaultDispatcher {
            val outcome = runCatching {
                val certProber = Libexclavecore.newCertProber().apply {
                    if (SagerNet.started && DataStore.startedProfile > 0) {
                        useUDS(app.cacheDir.toString() + "/ipc.sock")
                    }
                }
                when (protocol) {
                    0 -> certProber.probeTLS(server, port, serverName, alpn)
                    1 -> certProber.probeQUIC(server, port, serverName, alpn)
                    else -> throw IllegalArgumentException("Unsupported protocol: $protocol")
                }
            }
            onMainDispatcher {
                if (isFinishing || isDestroyed) return@onMainDispatcher
                setLoading(false)
                val result = outcome.getOrElse { error ->
                    binding.certificate.setText("")
                    showErrorDialog(error.readableMessage)
                    return@onMainDispatcher
                }
                if (result.error.isNotEmpty()) {
                    binding.certificate.setText("")
                    showErrorDialog(result.error)
                } else {
                    binding.certificate.setText(result.cert)
                    if (result.verifyError.isNotEmpty()) {
                        showErrorDialog(result.verifyError)
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finishAndRemoveTask()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

}
