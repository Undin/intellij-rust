/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath

val Sdk.toolchain: RustToolchain?
    get() {
        val homePath = homePath?.toPath() ?: return null
        val toolchainName = rustData?.toolchainName
        return RustToolchain(homePath, toolchainName)
    }

val Sdk.explicitPathToStdlib: String? get() = rustData?.stdlibPath

private val Sdk.rustData: RsSdkAdditionalData?
    get() = sdkAdditionalData as? RsSdkAdditionalData

object RsSdkUtils {

    fun getAllRustSdks(): List<Sdk> =
        ProjectJdkTable.getInstance().getSdksOfType(RsSdkType.getInstance())

    fun findSdkByName(name: String): Sdk? =
        ProjectJdkTable.getInstance().findJdk(name, RsSdkType.getInstance().name)

    fun detectRustSdks(
        existingSdks: List<Sdk>,
        flavors: List<RsSdkFlavor> = RsSdkFlavor.getApplicableFlavors()
    ): List<RsDetectedSdk> {
        val existingPaths = existingSdks
            .mapNotNull { it.homePath }
            .filterNot { RustupSdkFlavor.isValidSdkPath(it) }
        return flavors.asSequence()
            .flatMap { it.suggestHomePaths() }
            .map { it.absolutePath }
            .distinct()
            .filterNot { it in existingPaths }
            .map { RsDetectedSdk(it) }
            .toList()
    }

    fun findOrCreateSdk(): Sdk? {
        val sdk = SdkConfigurationUtil.findOrCreateSdk(RsSdkComparator, RsSdkType.getInstance())
        if (sdk?.getOrCreateAdditionalData() == null) return null
        return sdk
    }

    fun isInvalid(sdk: Sdk): Boolean {
        val toolchain = sdk.homeDirectory
        return toolchain == null || !toolchain.exists()
    }

    fun changeSdkModificator(sdk: Sdk, modificator: SdkModificator?, processor: (SdkModificator) -> Boolean) {
        val name = sdk.name
        TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState())
        invokeAndWaitIfNeeded {
            val sdkInsideInvoke = findSdkByName(name)
            val effectiveModificator = modificator ?: sdkInsideInvoke?.sdkModificator ?: sdk.sdkModificator
            if (processor(effectiveModificator)) {
                effectiveModificator.commitChanges()
            }
        }
    }

    private fun Sdk.getOrCreateAdditionalData(): RsSdkAdditionalData? {
        val existingData = sdkAdditionalData
        if (existingData != null) {
            return sdkAdditionalData as? RsSdkAdditionalData
        }

        val homePath = homePath?.toPath() ?: return null
        val rustup = RustToolchain(homePath, null).rustup()

        val newData = RsSdkAdditionalData()
        if (rustup != null) {
            val project = ProjectManager.getInstance().defaultProject
            newData.toolchainName = project.computeWithCancelableProgress("Fetching default toolchain...") {
                rustup.listToolchains().find { it.isDefault }?.name
            }
        }

        changeSdkModificator(this, null) { modificatorToWrite ->
            modificatorToWrite.sdkAdditionalData = newData
            true
        }

        return newData
    }
}
