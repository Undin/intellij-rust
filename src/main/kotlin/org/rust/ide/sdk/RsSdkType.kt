/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import org.jdom.Element
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsSdkUtils.detectRustSdks
import org.rust.ide.sdk.add.RsAddSdkDialog
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.openapiext.computeWithCancelableProgress
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Class should be final and singleton since some code checks its instance by ref.
 */
class RsSdkType : SdkType(RUST_SDK_ID_NAME) {

    override fun getIcon(): Icon = RsIcons.RUST

    override fun getHelpTopic(): String = "reference.project.structure.sdk.rust"

    override fun getIconForAddAction(): Icon = RsIcons.RUST_FILE

    override fun suggestHomePath(): String? = suggestHomePaths().firstOrNull()

    override fun suggestHomePaths(): Collection<String> {
        val existingSdks = ProjectJdkTable.getInstance().allJdks.toList()
        return detectRustSdks(existingSdks).mapNotNull { it.homePath }
    }

    override fun isValidSdkHome(path: String?): Boolean = RsSdkFlavor.getFlavor(path) != null

    override fun getHomeChooserDescriptor(): FileChooserDescriptor =
        object : FileChooserDescriptor(false, true, false, false, false, false) {
            override fun validateSelectedFiles(files: Array<VirtualFile>) {
                val file = files.firstOrNull() ?: return
                if (!isValidSdkHome(file.path)) {
                    throw Exception("Selected directory '${file.name}' isn't Rust toolchain")
                }
            }
        }.withTitle("Select Rust Toolchain Path").withShowHiddenFiles(SystemInfo.isUnix)

    override fun supportsCustomCreateUI(): Boolean = true

    override fun showCustomCreateUI(
        sdkModel: SdkModel,
        parentComponent: JComponent,
        selectedSdk: Sdk?,
        sdkCreatedCallback: Consumer<Sdk>
    ) {
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
        RsAddSdkDialog.show(project, sdkModel.sdks.toList()) { sdk ->
            if (sdk != null) {
                sdk.putUserData(SDK_CREATOR_COMPONENT_KEY, WeakReference(parentComponent))
                sdkCreatedCallback.consume(sdk)
            }
        }
    }

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String = "Rust"

    override fun createAdditionalDataConfigurable(
        sdkModel: SdkModel,
        sdkModificator: SdkModificator
    ): AdditionalDataConfigurable = RsAdditionalDataConfigurable()

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
        (additionalData as? RsSdkAdditionalData)?.save(additional)
    }

    override fun loadAdditionalData(additional: Element): SdkAdditionalData =
        RsSdkAdditionalData().apply { load(additional) }

    override fun getPresentableName(): String = RUST_SDK_ID_NAME

    override fun sdkPath(homePath: VirtualFile): String {
        val path = super.sdkPath(homePath)
        return FileUtil.toSystemDependentName(path)
    }

    override fun getVersionString(sdk: Sdk): String? {
        val toolchain = sdk.toolchain ?: return null
        val project = ProjectManager.getInstance().defaultProject
        val versionInfo = project.computeWithCancelableProgress("Fetching rustc version...") {
            toolchain.queryVersions()
        }
        return versionInfo.rustc?.semver?.parsedVersion
    }

    override fun isRootTypeApplicable(type: OrderRootType): Boolean = false

    override fun sdkHasValidPath(sdk: Sdk): Boolean = sdk.homeDirectory?.isValid ?: false

    companion object {
        private const val RUST_SDK_ID_NAME: String = "Rust SDK"

        private val SDK_CREATOR_COMPONENT_KEY: Key<WeakReference<JComponent>> =
            Key.create("#org.rust.ide.sdk.creatorComponent")

        fun getInstance(): RsSdkType = findInstance(RsSdkType::class.java)
    }
}
