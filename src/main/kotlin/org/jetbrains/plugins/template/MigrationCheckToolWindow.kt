package org.jetbrains.plugins.template

import com.intellij.ui.treeStructure.Tree
import java.awt.GridLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


class MigrationCheckToolWindow(): JPanel() {

    private val model = DefaultTreeModel( DefaultMutableTreeNode(""), false)
    private val tree = Tree(model)

    private fun initComponents() {
        layout = GridLayout()
        add(tree)
    }

    fun update(referenz: Referenz) {
        val newRoot = DefaultMutableTreeNode(referenz.name)
        model.setRoot(newRoot)
        referenz.wirdReferenziertVon.buildNodesRecursive().forEach { newRoot.add(it) }
        for (i in 0..tree.rowCount){
            tree.expandRow(i)
        }
        tree.isVisible = true
        tree.revalidate()
        tree.repaint()
    }

    init {
        initComponents()
    }
}

private fun List<Referenz>.buildNodesRecursive(): List<DefaultMutableTreeNode> {
    return map { referenz ->
        DefaultMutableTreeNode(referenz.name).also { node ->
            referenz.wirdReferenziertVon.buildNodesRecursive().forEach { node.add(it) }
        }
    }
}
