/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import org.rust.cargo.toolchain.RustToolchain.Companion.CARGO
import org.rust.cargo.toolchain.RustToolchain.Companion.RUSTC
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

interface RsSdkFlavor {

    fun suggestHomePaths(): Sequence<File> = getHomePathCandidates().distinct().filter { isValidSdkHome(it) }

    fun getHomePathCandidates(): Sequence<File>

    /**
     * Flavor is added to result in [getApplicableFlavors] if this method returns true.
     * @return whether this flavor is applicable.
     */
    fun isApplicable(): Boolean = true

    /**
     * Checks if the path is the name of a Python interpreter of this flavor.
     *
     * @param sdkPath path to check.
     * @return true if paths points to a valid home.
     */
    fun isValidSdkPath(sdkPath: String): Boolean {
        val sdkHome = File(sdkPath)
        return sdkHome.isDirectory && isValidSdkHome(sdkHome)
    }

    fun isValidSdkHome(sdkHome: File): Boolean = sdkHome.hasExecutable(RUSTC) && sdkHome.hasExecutable(CARGO)

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<RsSdkFlavor> = ExtensionPointName.create("org.rust.sdkFlavor")

        fun getApplicableFlavors(): List<RsSdkFlavor> = EP_NAME.extensionList.filter { it.isApplicable() }

        fun getFlavor(sdk: Sdk): RsSdkFlavor? = getFlavor(sdk.homePath)

        fun getFlavor(sdkPath: String?): RsSdkFlavor? {
            if (sdkPath == null) return null
            return getApplicableFlavors().find { flavor -> flavor.isValidSdkPath(sdkPath) }
        }

        // TODO: Move?
        @JvmStatic
        fun File.hasExecutable(toolName: String): Boolean = Files.isExecutable(pathToExecutable(toolName))

        private fun File.pathToExecutable(toolName: String): Path {
            val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
            return resolve(exeName).absolutePath.toPath()
        }
    }
}
