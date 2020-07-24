/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.LayeredIcon
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import javax.swing.Icon

data class SdkName(val primary: String, val secondary: String?, val modifier: String?)

enum class RsRenderedSdkType { RUSTUP, CARGO }

object RsSdkRenderingUtils {
    const val noToolchainMarker: String = "<No toolchain>"

    fun name(sdk: Sdk, sdkModificator: SdkModificator? = null): SdkName =
        name(sdk, sdkModificator?.name ?: sdk.name)

    /**
     * Returns modifier that shortly describes that is wrong with passed [sdk], [name] and additional info.
     */
    fun name(sdk: Sdk, name: String): SdkName {
        val modifier = if (RsSdkUtils.isInvalid(sdk)) "invalid" else null
        val secondary = sdk.versionString
        return SdkName(name, secondary, modifier)
    }

    /**
     * Returns a path to be rendered as the sdk's path.
     *
     * Initial value is taken from the [sdkModificator] or the [sdk] itself,
     * then it is converted to a path relative to the user home directory.
     *
     * Returns null if the initial path or the relative value are presented in the sdk's name.
     *
     * @see FileUtil.getLocationRelativeToUserHome
     */
    fun path(sdk: Sdk, sdkModificator: SdkModificator? = null): String? {
        val name = sdkModificator?.name ?: sdk.name
        val homePath = sdkModificator?.homePath ?: sdk.homePath ?: return null
        return FileUtil.getLocationRelativeToUserHome(homePath).takeIf { homePath !in name && it !in name }
    }

    fun icon(sdk: Sdk): Icon? {
        val icon = (sdk.sdkType as? SdkType)?.icon ?: return null
        return when {
            RsSdkUtils.isInvalid(sdk) -> wrapIconWithWarningDecorator(icon)
            sdk is RsDetectedSdk -> IconLoader.getTransparentIcon(icon)
            else -> icon
        }
    }

    fun groupSdksByTypes(allSdks: List<Sdk>, isInvalid: (Sdk) -> Boolean): Map<RsRenderedSdkType, List<Sdk>> =
        allSdks.asSequence()
            .filterNot(isInvalid)
            .groupBy {
                val homePath = it.homePath
                if (homePath != null && RustupSdkFlavor.isValidSdkPath(homePath)) {
                    RsRenderedSdkType.RUSTUP
                } else {
                    RsRenderedSdkType.CARGO
                }
            }

    private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon =
        LayeredIcon(2).apply {
            setIcon(icon, 0)
            setIcon(AllIcons.Actions.Cancel, 1)
        }
}
