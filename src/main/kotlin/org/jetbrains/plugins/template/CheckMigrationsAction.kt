package org.jetbrains.plugins.template

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.util.concurrent.ConcurrentHashMap

class CheckMigrationsAction: AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val psi = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val caretModel = editor?.caretModel

        val classOfSelection =
                PsiTreeUtil.getParentOfType(psi?.findElementAt(caretModel?.offset ?: 0), KtClass::class.java)

        if (classOfSelection != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                runReadAction {
                    ProgressManager.getInstance()
                            .runProcess(MigrationCalculator(classOfSelection, e.project), EmptyProgressIndicator())
                }
            }
        }
    }
}

class MigrationCalculator(val classOfSelection: KtClass, val project: Project?): Runnable {

    val knownNames = ConcurrentHashMap.newKeySet<KtClass?>()

    override fun run() {
        runReadAction {

            val name = classOfSelection.name ?: ""
            val fqname = classOfSelection.fqName?.asString() ?: ""
            val ref = Referenz(name, fqname, classOfSelection,
                               findeReferenzen(classOfSelection))

            println(ref)

            val content = project?.let {
                ToolWindowManager.getInstance(it)
                        .getToolWindow("topicMigrations")?.contentManager?.getContent(0)
            }

            (content?.component as? MigrationCheckToolWindow)?.update(ref)
        }
    }

    private fun findeReferenzen(element: KtClass): List<Referenz> {
        return ReferencesSearch.search(element.originalElement,
                                       GlobalSearchScopes.projectProductionScope(element.project), false)
                .filter { it.element.context is KtUserType }
                .mapNotNull { it.element.getParentOfType<KtClass>(true) }
                .filter { !knownNames.contains(it) }
                .onEach { knownNames.add(it) }
                .filter { it.isData() || it.isSealed() || it.isInterface() || it.isEreignis() }
                .map { cclas ->
                    val fqname = cclas.fqName?.asString() ?: ""
                    val name = cclas.name ?: ""
                    Referenz(name, fqname, cclas, findeReferenzen(cclas).filter { refs -> refs.hatEreignis() })

                }
    }
}

fun KtClass.isEreignis() = runReadAction {
    modifierList?.annotationEntries?.map { anno -> anno.shortName.toString() }?.contains("EreignisMetadaten") ?: false
}

data class Referenz(val name: String, val fqname: String, val psiClass: KtClass,
                    val wirdReferenziertVon: List<Referenz>) {

    fun hatEreignis(): Boolean = runReadAction { psiClass.isEreignis() || wirdReferenziertVon.any { it.hatEreignis() } }
}
