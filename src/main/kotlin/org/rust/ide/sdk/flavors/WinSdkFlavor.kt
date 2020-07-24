/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import java.io.File

class WinSdkFlavor private constructor() : RsSdkFlavor {

    override fun getHomePathCandidates(): Sequence<File> {
        val programFiles = File(System.getenv("ProgramFiles") ?: "")
        if (!programFiles.exists() || !programFiles.isDirectory) return emptySequence()
        return programFiles.listFiles { file -> file.isDirectory }.asSequence()
            .filter { it.nameWithoutExtension.toLowerCase().startsWith("rust") }
            .map { File(it, "bin") }
    }

    override fun isApplicable(): Boolean = SystemInfo.isWindows

    companion object {
        fun getInstance(): RsSdkFlavor? = RsSdkFlavor.EP_NAME.findExtension(WinSdkFlavor::class.java)
    }
}
