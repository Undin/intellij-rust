/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOnlyAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class RsCurrentActionService {
    var isGoToDeclarationAction: Boolean = false
        private set

    private class Listener : AnActionListener {
        override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
            if (action is GotoDeclarationAction || action is GotoDeclarationOnlyAction) {
                getInstance().isGoToDeclarationAction = true
            }
        }

        override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
            if (action is GotoDeclarationAction || action is GotoDeclarationOnlyAction) {
                getInstance().isGoToDeclarationAction = false
            }
        }
    }

    companion object {
        fun getInstance(): RsCurrentActionService = service()
    }
}
