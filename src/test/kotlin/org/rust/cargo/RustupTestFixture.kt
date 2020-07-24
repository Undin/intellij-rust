/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.impl.BaseFixture
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.util.DownloadResult
import org.rust.ide.sdk.RsSdkUtils.findOrCreateSdk
import org.rust.ide.sdk.toolchain

// TODO: use it in [org.rust.WithRustup]
class RustupTestFixture(
    // This property is mutable to allow `com.intellij.testFramework.UsefulTestCase.clearDeclaredFields`
    // set null in `tearDown`
    private var project: Project
) : BaseFixture() {

    val sdk: Sdk? by lazy { findOrCreateSdk() }
    val rustup: Rustup? by lazy { sdk?.toolchain?.rustup() }
    val stdlib: VirtualFile? by lazy { (rustup?.downloadStdlib() as? DownloadResult.Ok)?.value }

    val skipTestReason: String?
        get() {
            if (rustup == null) return "No rustup"
            if (stdlib == null) return "No stdlib"
            return null
        }

    override fun setUp() {
        super.setUp()
        stdlib?.let { VfsRootAccess.allowRootAccess(testRootDisposable, it.path) }
        if (sdk != null) {
            project.rustSettings.modify { it.sdk = sdk }
        }
    }

    override fun tearDown() {
        project.rustSettings.modify { it.sdk = null }
        val sdk = sdk
        if (sdk != null) {
            runWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
        }
        super.tearDown()
    }
}
