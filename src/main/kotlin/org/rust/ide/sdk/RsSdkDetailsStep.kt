/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import org.rust.ide.sdk.add.RsAddSdkDialog
import java.awt.Point
import javax.swing.JComponent

class RsSdkDetailsStep(
    private val project: Project?,
    private val showAll: DialogWrapper?,
    private val existingSdks: Array<Sdk>,
    private val sdkAddedCallback: (Sdk?) -> Unit
) : BaseListPopupStep<String>(null, getAvailableOptions(showAll != null)) {

    override fun getSeparatorAbove(value: String): ListSeparator? =
        if (ALL == value) ListSeparator() else null

    override fun canceled() {
        if (finalRunnable == null && showAll != null) {
            Disposer.dispose(showAll.disposable)
        }
    }

    override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? =
        doFinalStep { optionSelected(selectedValue) }

    private fun optionSelected(selectedValue: String?) {
        if (ALL != selectedValue && showAll != null) {
            Disposer.dispose(showAll.disposable)
        }

        if (ADD == selectedValue) {
            RsAddSdkDialog.show(project, existingSdks.toList(), sdkAddedCallback)
        } else {
            showAll?.show()
        }
    }

    companion object {
        private const val ADD: String = "Add..."
        private const val ALL: String = "Show All..."

        fun show(
            project: Project?,
            existingSdks: Array<Sdk>,
            showAllDialog: DialogWrapper,
            ownerComponent: JComponent,
            popupPoint: Point,
            sdkAddedCallback: (Sdk?) -> Unit
        ) {
            val sdkHomesStep = RsSdkDetailsStep(project, showAllDialog, existingSdks, sdkAddedCallback)
            val popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep)
            popup.showInScreenCoordinates(ownerComponent, popupPoint)
        }

        private fun getAvailableOptions(showAll: Boolean): List<String> =
            listOfNotNull(ADD, ALL.takeIf { showAll })
    }
}
