package smarthex

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page under Settings | Tools | Smart Hex Preview.
 * Lets the user add / remove / reset the regex patterns used to recognize hex colors.
 */
class SettingsPanel : Configurable {

    private var model: DefaultListModel<String>? = null
    private var list: JBList<String>? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Smart Hex Preview"

    override fun createComponent(): JComponent? {
        val m = DefaultListModel<String>()
        HexPatternConfig.getInstance().patterns.forEach { m.addElement(it) }
        model = m
        val l = JBList(m)
        list = l

        val addBtn = JButton("Add")
        addBtn.addActionListener {
            val input = Messages.showInputDialog(
                "Regex pattern.\nUse a capturing group around #XXXXXX, e.g. \\[(#......)\\]",
                "Add Pattern",
                Messages.getQuestionIcon()
            )
            if (!input.isNullOrBlank()) {
                val trimmed = input.trim()
                try {
                    Pattern.compile(trimmed)
                    m.addElement(trimmed)
                } catch (e: PatternSyntaxException) {
                    Messages.showErrorDialog(
                        "Invalid regex pattern:\n${e.message}",
                        "Pattern Error"
                    )
                }
            }
        }

        val removeBtn = JButton("Remove")
        removeBtn.addActionListener {
            val i = l.selectedIndex
            if (i >= 0) m.remove(i)
        }

        val resetBtn = JButton("Reset to Defaults")
        resetBtn.addActionListener {
            m.clear()
            HexPatternConfig.defaultPatterns.forEach { m.addElement(it) }
        }

        val buttons = JPanel()
        buttons.add(addBtn)
        buttons.add(removeBtn)
        buttons.add(resetBtn)

        val p = JPanel(BorderLayout())
        p.add(JBScrollPane(l), BorderLayout.CENTER)
        p.add(buttons, BorderLayout.SOUTH)
        panel = p
        return p
    }

    override fun isModified(): Boolean {
        val m = model ?: return false
        val current = HexPatternConfig.getInstance().patterns
        if (m.size() != current.size) return true
        for (i in 0 until m.size()) {
            if (m[i] != current[i]) return true
        }
        return false
    }

    override fun apply() {
        val m = model ?: return
        val newPatterns = (0 until m.size()).map { m[it] }
        // Validate all patterns before saving
        for (p in newPatterns) {
            try {
                Pattern.compile(p)
            } catch (e: PatternSyntaxException) {
                Messages.showErrorDialog(
                    "Cannot apply — invalid regex pattern:\n$p\n${e.message}",
                    "Pattern Error"
                )
                return
            }
        }
        HexPatternConfig.getInstance().setPatterns(newPatterns)
        ProjectManager.getInstance().openProjects.forEach { prj ->
            HexHighlightingService.getInstance(prj).refreshAll()
        }
    }

    override fun reset() {
        val m = model ?: return
        m.clear()
        HexPatternConfig.getInstance().patterns.forEach { m.addElement(it) }
    }
}
