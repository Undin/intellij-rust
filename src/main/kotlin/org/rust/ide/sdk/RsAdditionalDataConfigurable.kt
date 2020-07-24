/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.ide.sdk.RsSdkUtils.changeSdkModificator
import org.rust.ide.ui.layout
import javax.swing.JComponent

class RsAdditionalDataConfigurable : AdditionalDataConfigurable {
    private var sdk: Sdk? = null
    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel(sdk?.homePath)
    private val toolchainList: RsConfigurableToolchainList = RsConfigurableToolchainList.getInstance(null)
    private val projectSdksModel: ProjectSdksModel = toolchainList.model
    private val listener: SdkModel.Listener = object : SdkModel.Listener {
        override fun sdkHomeSelected(sdk: Sdk, newSdkHome: String) {
            if (sdk === this@RsAdditionalDataConfigurable.sdk) {
                sdkAdditionalDataPanel.update(newSdkHome)
            }
        }
    }

    init {
        projectSdksModel.addListener(listener)
    }

    override fun setSdk(sdk: Sdk) {
        this.sdk = sdk
        sdkAdditionalDataPanel.update(sdk.homePath)
    }

    override fun createComponent(): JComponent = layout {
        sdkAdditionalDataPanel.attachTo(this)
    }

    override fun isModified(): Boolean = sdk?.sdkAdditionalData != sdkAdditionalDataPanel.data

    override fun apply() {
        sdkAdditionalDataPanel.validateSettings()

        val sdk = sdk ?: return
        changeSdkModificator(sdk, null) { modificatorToWrite ->
            modificatorToWrite.sdkAdditionalData = sdkAdditionalDataPanel.data
            true
        }
    }

    override fun reset() {
        sdkAdditionalDataPanel.data = sdk?.sdkAdditionalData as? RsSdkAdditionalData
    }

    override fun disposeUIResources() = projectSdksModel.removeListener(listener)
}
