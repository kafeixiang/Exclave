/******************************************************************************
 *                                                                            *
 * Copyright (C) 2023  dyhkwong                                               *
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

package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic5.Tuic5Bean
import io.nekohasekai.sagernet.ktx.getBooleanProperty
import io.nekohasekai.sagernet.ktx.unwrapIDN
import io.nekohasekai.sagernet.widget.SimpleMenuPreference

open class Tuic5SettingsActivity : ProfileSettingsActivity<AbstractBean>() {

    override fun createEntity(): AbstractBean = Tuic5Bean()

    override fun AbstractBean.init() {
        if (this is TuicBean) {
            DataStore.serverTuicVersion = 4
            DataStore.profileName = name
            DataStore.serverAddress = serverAddress
            DataStore.serverPort = serverPort
            DataStore.serverPassword = token
            DataStore.serverALPN = alpn
            DataStore.serverCertificates = caText
            DataStore.serverUDPRelayMode = udpRelayMode
            DataStore.serverCongestionController = congestionController
            DataStore.serverDisableSNI = disableSNI
            DataStore.serverSNI = sni
            DataStore.serverReduceRTT = reduceRTT
            DataStore.serverAllowInsecure = allowInsecure
            DataStore.serverMTU = mtu
            DataStore.serverHeartbeatInterval = heartbeat
        } else if (this is Tuic5Bean) {
            DataStore.serverTuicVersion = 5
            DataStore.profileName = name
            DataStore.serverAddress = serverAddress
            DataStore.serverPort = serverPort
            DataStore.serverUserId = uuid
            DataStore.serverPassword = password
            DataStore.serverALPN = alpn
            DataStore.serverCertificates = certificates
            DataStore.serverUDPRelayMode = udpRelayMode
            DataStore.serverCongestionController = congestionControl
            DataStore.serverDisableSNI = disableSNI
            DataStore.serverSNI = sni
            DataStore.serverReduceRTT = zeroRTTHandshake
            DataStore.serverAllowInsecure = allowInsecure
            DataStore.serverPinnedCertificateChain = pinnedPeerCertificateChainSha256
            DataStore.serverPinnedCertificatePublicKey = pinnedPeerCertificatePublicKeySha256
            DataStore.serverPinnedCertificate = pinnedPeerCertificateSha256
            DataStore.serverMtlsCertificate = mtlsCertificate
            DataStore.serverMtlsCertificatePrivateKey = mtlsCertificatePrivateKey
            DataStore.serverEchEnabled = echEnabled
            DataStore.serverEchConfigList = echConfigList
            DataStore.serverEchQueryName = echQueryName
            DataStore.serverServerNameToVerify = serverNameToVerify
            DataStore.serverSingUot = singUDPOverStream
        }
    }

    override fun AbstractBean.serialize() {
        if (this is TuicBean) {
            name = DataStore.profileName
            serverAddress = DataStore.serverAddress.unwrapIDN()
            serverPort = DataStore.serverPort
            token = DataStore.serverPassword
            alpn = DataStore.serverALPN
            caText = DataStore.serverCertificates
            udpRelayMode = DataStore.serverUDPRelayMode
            congestionController = DataStore.serverCongestionController
            disableSNI = DataStore.serverDisableSNI
            sni = DataStore.serverSNI
            reduceRTT = DataStore.serverReduceRTT
            allowInsecure = DataStore.serverAllowInsecure
            mtu = DataStore.serverMTU
            heartbeat = DataStore.serverHeartbeatInterval
        } else if (this is Tuic5Bean) {
            name = DataStore.profileName
            serverAddress = DataStore.serverAddress.unwrapIDN()
            serverPort = DataStore.serverPort
            uuid = DataStore.serverUserId
            password = DataStore.serverPassword
            alpn = DataStore.serverALPN
            certificates = DataStore.serverCertificates
            udpRelayMode = DataStore.serverUDPRelayMode
            congestionControl = DataStore.serverCongestionController
            disableSNI = DataStore.serverDisableSNI
            sni = DataStore.serverSNI
            zeroRTTHandshake = DataStore.serverReduceRTT
            allowInsecure = DataStore.serverAllowInsecure
            pinnedPeerCertificateChainSha256 = DataStore.serverPinnedCertificateChain
            pinnedPeerCertificatePublicKeySha256 = DataStore.serverPinnedCertificatePublicKey
            pinnedPeerCertificateSha256 = DataStore.serverPinnedCertificate
            mtlsCertificate = DataStore.serverMtlsCertificate
            mtlsCertificatePrivateKey = DataStore.serverMtlsCertificatePrivateKey
            echEnabled = DataStore.serverEchEnabled
            echConfigList = DataStore.serverEchConfigList
            echQueryName = DataStore.serverEchQueryName
            serverNameToVerify = DataStore.serverServerNameToVerify
            singUDPOverStream = DataStore.serverSingUot
        }
    }

    override suspend fun saveAndExit() {
        val version = DataStore.serverTuicVersion
        val editingId = DataStore.editingId
        val targetBean: AbstractBean = if (version == 4) {
            TuicBean().apply {
                name = DataStore.profileName
                serverAddress = DataStore.serverAddress.unwrapIDN()
                serverPort = DataStore.serverPort
                token = DataStore.serverPassword
                alpn = DataStore.serverALPN
                caText = DataStore.serverCertificates
                udpRelayMode = DataStore.serverUDPRelayMode
                congestionController = DataStore.serverCongestionController
                disableSNI = DataStore.serverDisableSNI
                sni = DataStore.serverSNI
                reduceRTT = DataStore.serverReduceRTT
                allowInsecure = DataStore.serverAllowInsecure
                mtu = DataStore.serverMTU.takeIf { it > 0 } ?: 1400
                heartbeat = DataStore.serverHeartbeatInterval.takeIf { it > 0 } ?: 10
            }
        } else {
            Tuic5Bean().apply {
                name = DataStore.profileName
                serverAddress = DataStore.serverAddress.unwrapIDN()
                serverPort = DataStore.serverPort
                uuid = DataStore.serverUserId
                password = DataStore.serverPassword
                alpn = DataStore.serverALPN
                certificates = DataStore.serverCertificates
                udpRelayMode = DataStore.serverUDPRelayMode
                congestionControl = DataStore.serverCongestionController
                disableSNI = DataStore.serverDisableSNI
                sni = DataStore.serverSNI
                zeroRTTHandshake = DataStore.serverReduceRTT
                allowInsecure = DataStore.serverAllowInsecure
                pinnedPeerCertificateChainSha256 = DataStore.serverPinnedCertificateChain
                pinnedPeerCertificatePublicKeySha256 = DataStore.serverPinnedCertificatePublicKey
                pinnedPeerCertificateSha256 = DataStore.serverPinnedCertificate
                mtlsCertificate = DataStore.serverMtlsCertificate
                mtlsCertificatePrivateKey = DataStore.serverMtlsCertificatePrivateKey
                echEnabled = DataStore.serverEchEnabled
                echConfigList = DataStore.serverEchConfigList
                echQueryName = DataStore.serverEchQueryName
                serverNameToVerify = DataStore.serverServerNameToVerify
                singUDPOverStream = DataStore.serverSingUot
            }
        }

        if (editingId == 0L) {
            val editingGroup = DataStore.editingGroup
            ProfileManager.createProfile(editingGroup, targetBean)
        } else {
            val entity = SagerDatabase.proxyDao.getById(editingId)
            if (entity == null) {
                finish()
                return
            }
            entity.putBean(targetBean)
            ProfileManager.updateProfile(entity)
        }
        setResult(RESULT_OK)
        finish()
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tuic5_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        findPreference<EditTextPreference>(Key.SERVER_HEARTBEAT_INTERVAL)?.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        findPreference<EditTextPreference>(Key.SERVER_MTU)?.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }

        val disableSNI = findPreference<SwitchPreference>(Key.SERVER_DISABLE_SNI)!!
        val sni = findPreference<EditTextPreference>(Key.SERVER_SNI)!!
        sni.isEnabled = !disableSNI.isChecked
        disableSNI.setOnPreferenceChangeListener { _, newValue ->
            sni.isEnabled = !(newValue as Boolean)
            true
        }

        val echEnabled = findPreference<SwitchPreference>(Key.SERVER_ECH_ENABLED)!!
        val echConfigList = findPreference<EditTextPreference>(Key.SERVER_ECH_CONFIG_LIST)!!
        val echQueryName = findPreference<EditTextPreference>(Key.SERVER_ECH_QUERY_NAME)!!
        echConfigList.isEnabled = echEnabled.isChecked
        echQueryName.isEnabled = echEnabled.isChecked
        echEnabled.setOnPreferenceChangeListener { _, newValue ->
            echConfigList.isEnabled = newValue as Boolean
            echQueryName.isEnabled = newValue
            true
        }

        val versionPref = findPreference<SimpleMenuPreference>(Key.SERVER_TUIC_VERSION)!!
        fun updateVisibility(version: Int) {
            val isV5 = version == 5
            val isV4 = version == 4

            findPreference<EditTextPreference>(Key.SERVER_USER_ID)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_MTU)?.isVisible = isV4
            findPreference<EditTextPreference>(Key.SERVER_HEARTBEAT_INTERVAL)?.isVisible = isV4

            findPreference<EditTextPreference>(Key.SERVER_PINNED_CERTIFICATE)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_PINNED_CERTIFICATE_PUBLIC_KEY)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_PINNED_CERTIFICATE_CHAIN)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_MTLS_CERTIFICATE)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_MTLS_CERTIFICATE_PRIVATE_KEY)?.isVisible = isV5
            findPreference<SwitchPreference>(Key.SERVER_ECH_ENABLED)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_ECH_CONFIG_LIST)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_ECH_QUERY_NAME)?.isVisible = isV5
            findPreference<EditTextPreference>(Key.SERVER_SERVER_NAME_TO_VERIFY)?.isVisible = isV5

            findPreference<PreferenceCategory>(Key.SERVER_SING_UOT_CATEGORY)?.isVisible =
                isV5 && DataStore.experimentalFlagsProperties.getBooleanProperty("singuot")

            findPreference<EditTextPreference>(Key.SERVER_PASSWORD)?.setTitle(
                if (isV4) R.string.tuic_token else R.string.password
            )

            findPreference<SwitchPreference>(Key.SERVER_REDUCE_RTT)?.setTitle(
                if (isV4) R.string.tuic_reduce_rtt else R.string.tuic_zero_rtt_handshake
            )
        }

        val initialVersion = versionPref.value?.toIntOrNull() ?: 5
        updateVisibility(initialVersion)

        versionPref.setOnPreferenceChangeListener { _, newValue ->
            val ver = (newValue as String).toIntOrNull() ?: 5
            updateVisibility(ver)
            true
        }
    }

}
