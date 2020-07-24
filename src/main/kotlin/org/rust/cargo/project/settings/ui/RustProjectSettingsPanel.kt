/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.util.ui.JBUI
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.ide.sdk.*
import org.rust.ide.sdk.RsSdkRenderingUtils.groupSdksByTypes
import org.rust.ide.ui.RsLayoutBuilder
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class RustProjectSettingsPanel(
    private val project: Project? = null,
    private val updateListener: (() -> Unit)? = null
) : Disposable {
    private val toolchainList: RsConfigurableToolchainList = RsConfigurableToolchainList.getInstance(project)
    private val projectSdksModel: ProjectSdksModel = toolchainList.model
    private val sdkComboBox: ComboBox<Any?> = buildSdkComboBox(::onShowAllSelected, ::onSdkSelected)
    private val mainPanel: JPanel = buildPanel(sdkComboBox, buildDetailsButton(sdkComboBox, ::onShowDetailsClicked))

    var sdk: Sdk?
        get() = sdkComboBox.selectedItem as? Sdk
        set(value) {
            updateSdkListAndSelect(value)
        }

    init {
        updateSdkListAndSelect(sdk)
    }

    fun attachTo(layout: RsLayoutBuilder) = layout.add(mainPanel)

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = sdk?.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    override fun dispose() {
        toolchainList.disposeModel()
    }

    private fun updateSdkListAndSelect(selectedSdk: Sdk?) {
        val items = mutableListOf<Any?>(null)

        val moduleSdksByTypes = groupSdksByTypes(toolchainList.allRustSdks, RsSdkUtils::isInvalid)
        for (currentSdkType in RsRenderedSdkType.values()) {
            if (currentSdkType in moduleSdksByTypes) {
                if (items.isNotEmpty()) items.add(RsSdkListCellRenderer.SEPARATOR)
                val moduleSdks = moduleSdksByTypes[currentSdkType] ?: continue
                items.addAll(moduleSdks)
            }
        }

        items.add(RsSdkListCellRenderer.SEPARATOR)
        items.add(SHOW_ALL)

        sdkComboBox.renderer = RsSdkListCellRenderer()
        val selection = selectedSdk?.let { projectSdksModel.findSdk(it.name) }
        sdkComboBox.model = CollectionComboBoxModel(items, selection)
        onSdkSelected()
    }

    private fun buildAllSdksDialog(): RsSdkDetailsDialog = RsSdkDetailsDialog(
        project,
        selectedSdkCallback = { selectedSdk ->
            if (selectedSdk != null) {
                updateSdkListAndSelect(selectedSdk)
            } else {
                val currentSelectedSdk = sdk
                if (currentSelectedSdk != null && projectSdksModel.findSdk(currentSelectedSdk.name) != null) {
                    // nothing has been selected but previously selected sdk still exists, stay with it
                    updateSdkListAndSelect(currentSelectedSdk)
                } else {
                    // nothing has been selected but previously selected sdk removed, switch to `No toolchain`
                    updateSdkListAndSelect(null)
                }
            }
        },
        cancelCallback = { reset ->
            // data is invalidated on `model` resetting so we need to reload sdks to not stuck with outdated ones
            if (reset) {
                updateSdkListAndSelect(sdk)
            }
        }
    )

    private fun onShowAllSelected() {
        buildAllSdksDialog().show()
    }

    private fun onShowDetailsClicked(detailsButton: JButton) {
        RsSdkDetailsStep.show(
            project,
            projectSdksModel.sdks,
            buildAllSdksDialog(),
            mainPanel,
            detailsButton.locationOnScreen
        ) { sdk ->
            if (sdk != null && projectSdksModel.findSdk(sdk.name) == null) {
                projectSdksModel.addSdk(sdk)
                try {
                    projectSdksModel.apply(null, true)
                } catch (e: ConfigurationException) {
                    LOG.error(e)
                }
                updateSdkListAndSelect(sdk)
            }
        }
    }

    private fun onSdkSelected() {
        updateListener?.invoke()
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(RustProjectSettingsPanel::class.java)

        private const val SHOW_ALL: String = "Show All..."

        private fun buildSdkComboBox(onShowAllSelected: () -> Unit, onSdkSelected: () -> Unit): ComboBox<Any?> {
            val result = object : ComboBox<Any?>() {
                override fun setSelectedItem(item: Any?) {
                    if (SHOW_ALL == item) {
                        onShowAllSelected()
                    } else if (item != RsSdkListCellRenderer.SEPARATOR) {
                        super.setSelectedItem(item)
                    }
                }
            }

            result.addItemListener { e -> if (e.stateChange == ItemEvent.SELECTED) onSdkSelected() }

            ComboboxSpeedSearch(result)
            result.preferredSize = result.preferredSize // this line allows making `result` resizable
            return result
        }

        private fun buildDetailsButton(sdkComboBox: ComboBox<*>, onShowDetails: (JButton) -> Unit): JButton {
            val result = FixedSizeButton(sdkComboBox.preferredSize.height)
            result.icon = AllIcons.General.GearPlain
            result.addActionListener { onShowDetails(result) }
            return result
        }

        // TODO: simplify
        private fun buildPanel(sdkComboBox: ComboBox<*>, detailsButton: JButton): JPanel {
            val result = JPanel(GridBagLayout())

            val c = GridBagConstraints()
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = JBUI.insets(2)

            c.gridx = 0
            c.gridy = 0
            result.add(JLabel("Toolchain:"), c)

            c.gridx = 1
            c.gridy = 0
            c.weightx = 0.1
            result.add(sdkComboBox, c)

            c.insets = JBUI.insets(2, 0, 2, 2)
            c.gridx = 2
            c.gridy = 0
            c.weightx = 0.0
            result.add(detailsButton, c)

            c.insets = JBUI.insets(2, 2, 0, 2)
            c.gridx = 0
            c.gridy++
            c.gridwidth = 3
            c.weightx = 0.0
            result.add(JLabel("  "), c)

            c.gridx = 0
            c.gridy++
            c.weighty = 1.0
            c.gridwidth = 3
            c.gridheight = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.BOTH

            c.gridheight = GridBagConstraints.REMAINDER
            c.gridx = 0
            c.gridy++
            c.gridwidth = 3
            c.weighty = 0.0
            c.fill = GridBagConstraints.HORIZONTAL
            c.anchor = GridBagConstraints.SOUTH

            return result
        }
    }
}
