/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.sdk.flavors.RsSdkFlavor.Companion.hasExecutable
import java.io.File

object RustupSdkFlavor : RsSdkFlavor {

    override fun getHomePathCandidates(): Sequence<File> {
        val file = File(FileUtil.expandUserHome("~/.cargo/bin"))
        return if (file.isDirectory) {
            sequenceOf(file)
        } else {
            emptySequence()
        }
    }

    override fun isValidSdkHome(sdkHome: File): Boolean =
        sdkHome.hasExecutable(RustToolchain.RUSTUP)
            && sdkHome.hasExecutable(RustToolchain.RUSTC)
            && sdkHome.hasExecutable(RustToolchain.CARGO)
}
