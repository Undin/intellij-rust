/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.startup.StartupActivity
import org.rust.ide.sdk.RsSdkUtils.changeSdkModificator
import org.rust.ide.sdk.RsSdkUtils.getAllRustSdks

/**
 * Refreshes all project's Rust SDKs.
 */
class RsSdkUpdater : StartupActivity.Background {
    /**
     * Refreshes the SDKs of the open project after some delay.
     */
    override fun runActivity(project: Project) {
        if (isUnitTestMode || project.isDisposed) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating Rust Toolchains", false) {
            override fun run(indicator: ProgressIndicator) {
                for (sdk in getAllRustSdks()) {
                    updateLocalSdkVersion(sdk, null)
                }
            }
        })
    }

    companion object {
        /**
         * Changes the version string of an SDK if it's out of date.
         *
         * May be invoked from any thread. May freeze the current thread while evaluating the run-time Rust version.
         */
        fun updateLocalSdkVersion(sdk: Sdk, sdkModificator: SdkModificator?) {
            val modificatorToRead = sdkModificator ?: sdk.sdkModificator
            val versionString = sdk.sdkType.getVersionString(sdk)
            if (versionString != modificatorToRead.versionString) {
                changeSdkModificator(sdk, null) { modificatorToWrite ->
                    modificatorToWrite.versionString = versionString
                    true
                }
            }
        }
    }
}
