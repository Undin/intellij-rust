/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import java.io.File

class SystemPathFlavor : RsSdkFlavor {

    override fun getHomePathCandidates(): Sequence<File> =
        System.getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotEmpty() }
            .map(::File)
            .filter { it.isDirectory }

    companion object {
        fun getInstance(): RsSdkFlavor? = RsSdkFlavor.EP_NAME.findExtension(SystemPathFlavor::class.java)
    }
}
