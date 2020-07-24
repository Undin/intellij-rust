/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil

class RsDetectedSdk(homePath: String) : ProjectJdkImpl(homePath, RsSdkType.getInstance()) {

    init {
        this.homePath = homePath
    }

    override fun getVersionString(): String? = ""

    fun setup(existingSdks: List<Sdk>): Sdk? {
        val homeDirectory = homeDirectory ?: return null
        return SdkConfigurationUtil.setupSdk(
            existingSdks.toTypedArray(),
            homeDirectory,
            RsSdkType.getInstance(),
            false,
            null,
            null
        )
    }
}
