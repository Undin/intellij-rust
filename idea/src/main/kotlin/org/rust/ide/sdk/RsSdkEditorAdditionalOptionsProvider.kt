/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.SdkEditorAdditionalOptionsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk

class RsSdkEditorAdditionalOptionsProvider : SdkEditorAdditionalOptionsProvider(RsSdkType.getInstance()) {
    override fun createOptions(project: Project, sdk: Sdk): AdditionalDataConfigurable? = null // RsAdditionalDataConfigurable()
}
