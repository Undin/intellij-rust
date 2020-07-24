/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import java.io.File

class MacSdkFlavor private constructor() : RsSdkFlavor {

    override fun getHomePathCandidates(): Sequence<File> =
        sequenceOf("/usr/local/Cellar/rust/bin").map(::File)

    override fun isApplicable(): Boolean = SystemInfo.isMac

    companion object {
        fun getInstance(): RsSdkFlavor? = RsSdkFlavor.EP_NAME.findExtension(MacSdkFlavor::class.java)
    }
}
