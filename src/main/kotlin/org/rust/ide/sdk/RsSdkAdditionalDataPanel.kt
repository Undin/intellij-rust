/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.Link
import com.intellij.util.text.SemVer
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import org.rust.ide.ui.RsLayoutBuilder
import org.rust.openapiext.ComboBoxDelegate
import org.rust.openapiext.UiDebouncer
import org.rust.openapiext.pathToDirectoryTextField
import org.rust.stdext.toPath
import javax.swing.JComponent
import javax.swing.JLabel

class RsSdkAdditionalDataPanel(private var homePath: String?) : Disposable {
    private val toolchain: RustToolchain?
        get() {
            val homePath = homePath?.toPath() ?: return null
            val toolchainName = rustupOverride?.name
            return RustToolchain(homePath, toolchainName)
        }

    var data: RsSdkAdditionalData?
        get() {
            if (homePath == null) return null
            return RsSdkAdditionalData(
                toolchainName = rustupOverride?.name?.takeIf { toolchain?.isRustupAvailable == true },
                stdlibPath = stdlibPathField.text.blankToNull()?.takeIf { toolchain?.isRustupAvailable == false }
            )
        }
        set(value) {
            val toolchain = (0 until rustupOverrideComboBox.itemCount)
                .map { rustupOverrideComboBox.getItemAt(it) }
                .find { it.name == value?.toolchainName }
            if (toolchain != null) {
                rustupOverrideComboBox.selectedItem = toolchain
            }
            stdlibPathField.text = value?.stdlibPath ?: ""
            update(homePath)
        }

    private val updateDebouncer: UiDebouncer = UiDebouncer(this)

    val rustupOverrideComboBox: ComboBox<Rustup.Toolchain> = ComboBox<Rustup.Toolchain>().apply {
        isEditable = false
        isEnabled = false
    }
    val rustupOverride: Rustup.Toolchain? by ComboBoxDelegate(rustupOverrideComboBox)

    private val toolchainVersion: JLabel = JLabel()

    private val stdlibPathField: TextFieldWithBrowseButton =
        pathToDirectoryTextField(this, "Select directory with standard library source code")

    private val downloadStdlibLink: JComponent = Link("Download via rustup", action = {
        val rustup = toolchain?.rustup() ?: return@Link
        object : Task.Backgroundable(null, "Downloading Rust standard library") {
            override fun shouldStartInBackground(): Boolean = false
            override fun onSuccess() = update(homePath)
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                rustup.downloadStdlib()
            }
        }.queue()
    }).apply { isVisible = false }

    init {
        // TODO: Update version on combobox change
//        rustupOverrideComboBox.addItemListener { e ->
//            if (e.stateChange == ItemEvent.SELECTED) update(homePath)
//        }
    }

    fun attachTo(layout: RsLayoutBuilder) = with(layout) {
        row("Rustup override:", rustupOverrideComboBox)
        row("Toolchain version:", toolchainVersion)
        row("Standard library:", stdlibPathField)
        row(component = downloadStdlibLink)
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    fun update(newHomePath: String?) {
        homePath = newHomePath

        data class Data(
            val toolchains: List<Rustup.Toolchain>,
            val rustcVersion: SemVer?,
            val stdlibPath: String?,
            val hasRustup: Boolean
        )

        updateDebouncer.run(
            onPooledThread = {
                val toolchain = toolchain
                val rustup = toolchain?.rustup()
                val toolchains = rustup?.listToolchains().orEmpty()
                val rustcVersion = toolchain?.queryVersions()?.rustc?.semver
                val stdlibLocation = toolchain?.getStdlibFromSysroot()?.presentableUrl
                Data(toolchains, rustcVersion, stdlibLocation, rustup != null)
            },
            onUiThread = { (toolchains, rustcVersion, stdlibLocation, hasRustup) ->
                rustupOverrideComboBox.removeAllItems()
                toolchains.forEach { rustupOverrideComboBox.addItem(it) }
                rustupOverrideComboBox.isEditable = hasRustup
                rustupOverrideComboBox.isEnabled = hasRustup

                if (rustcVersion == null) {
                    toolchainVersion.text = "N/A"
                    toolchainVersion.foreground = JBColor.RED
                } else {
                    toolchainVersion.text = rustcVersion.parsedVersion
                    toolchainVersion.foreground = JBColor.foreground()
                }

                stdlibPathField.isEditable = !hasRustup
                stdlibPathField.button.isEnabled = !hasRustup
                if (stdlibLocation != null) {
                    stdlibPathField.text = stdlibLocation
                }

                downloadStdlibLink.isVisible = hasRustup && stdlibLocation == null
            }
        )
    }

    override fun dispose() {}

    companion object {
        fun validateSdkAdditionalDataPanel(panel: RsSdkAdditionalDataPanel): ValidationInfo? {
            val homePath = panel.homePath ?: return null
            if (!RustupSdkFlavor.isValidSdkPath(homePath)) return null
            if (panel.rustupOverride != null) return null
            return ValidationInfo("Rustup override is not selected", panel.rustupOverrideComboBox)
        }
    }
}

private fun String.blankToNull(): String? = if (isBlank()) null else this
