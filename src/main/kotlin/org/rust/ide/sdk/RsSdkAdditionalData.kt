/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.SdkAdditionalData
import org.jdom.Element

data class RsSdkAdditionalData(
    var toolchainName: String? = null,
    var stdlibPath: String? = null
) : SdkAdditionalData {

    private constructor(from: RsSdkAdditionalData) : this(from.toolchainName, from.stdlibPath)

    fun copy(): RsSdkAdditionalData = RsSdkAdditionalData(this)

    fun save(rootElement: Element) {
        toolchainName?.let { rootElement.setAttribute(TOOLCHAIN_NAME, it) }
        stdlibPath?.let { rootElement.setAttribute(STDLIB_PATH, it) }
    }

    fun load(element: Element?) {
        if (element == null) return
        toolchainName = element.getAttributeValue(TOOLCHAIN_NAME)
        stdlibPath = element.getAttributeValue(STDLIB_PATH)
    }

    companion object {
        private const val TOOLCHAIN_NAME: String = "TOOLCHAIN_NAME"
        private const val STDLIB_PATH: String = "STDLIB_PATH"
    }
}
