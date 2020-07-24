/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsDetectedSdk
import org.rust.ide.sdk.RsSdkAdditionalDataPanel
import org.rust.ide.sdk.RsSdkAdditionalDataPanel.Companion.validateSdkAdditionalDataPanel
import org.rust.ide.sdk.RsSdkUtils.changeSdkModificator
import org.rust.ide.sdk.RsSdkUtils.detectRustSdks
import org.rust.ide.sdk.add.RsSdkPathChoosingComboBox.Companion.addToolchainsAsync
import org.rust.ide.sdk.add.RsSdkPathChoosingComboBox.Companion.validateSdkComboBox
import org.rust.ide.ui.layout
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.Icon

class RsAddLocalToolchainPanel(private val existingSdks: List<Sdk>) : RsAddToolchainPanel() {
    override val panelName: String = "Local toolchain"
    override val icon: Icon = RsIcons.RUST

    private val sdkComboBox: RsSdkPathChoosingComboBox = RsSdkPathChoosingComboBox()
    private val homePath: String? get() = sdkComboBox.selectedSdk?.homePath

    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel(homePath)

    init {
        layout = BorderLayout()
        val formPanel = layout {
            row("Toolchain path:", sdkComboBox)
            sdkAdditionalDataPanel.attachTo(this)
        }
        add(formPanel, BorderLayout.NORTH)
        addToolchainsAsync(sdkComboBox) { detectRustSdks(existingSdks) }
        sdkComboBox.childComponent.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                sdkAdditionalDataPanel.update(homePath)
            }
        }
    }

    override fun getOrCreateSdk(): Sdk? {
        var sdk = sdkComboBox.selectedSdk
        if (sdk is RsDetectedSdk) {
            sdk = sdk.setup(existingSdks)
        }
        if (sdk == null) return null
        changeSdkModificator(sdk, null) { modificatorToWrite ->
            modificatorToWrite.sdkAdditionalData = sdkAdditionalDataPanel.data
            true
        }
        return sdk
    }

    override fun validateAll(): List<ValidationInfo> =
        listOfNotNull(validateSdkComboBox(sdkComboBox), validateSdkAdditionalDataPanel(sdkAdditionalDataPanel))
}
