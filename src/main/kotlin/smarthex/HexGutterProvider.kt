package smarthex

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.Objects
import javax.swing.Icon

/**
 * Builds the clickable 12x12 gutter color indicator. Clicking it opens the color picker
 * (see [HexColorPickerHandler]) which replaces the hex value in place.
 */
object HexGutterProvider {
    fun createRenderer(
        project: Project,
        editor: Editor,
        highlighter: RangeHighlighter,
        color: Color
    ): GutterIconRenderer = HexGutterRenderer(project, editor, highlighter, color)
}

private class HexGutterRenderer(
    private val project: Project,
    private val editor: Editor,
    private val highlighter: RangeHighlighter,
    private val color: Color
) : GutterIconRenderer() {

    override fun getIcon(): Icon = HexColorIcon(color)

    override fun getTooltipText(): String? = "Click to change color"

    override fun getClickAction(): AnAction = object : AnAction("Pick Color") {
        override fun actionPerformed(e: AnActionEvent) {
            HexColorPickerHandler.chooseAndReplace(project, editor, highlighter)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HexGutterRenderer) return false
        return color == other.color && editor === other.editor
    }

    override fun hashCode(): Int = Objects.hash(color, editor)
}

/** A 12x12 rounded-square icon filled with the given color. */
class HexColorIcon(private val color: Color) : Icon {
    override fun getIconWidth(): Int = 12
    override fun getIconHeight(): Int = 12

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fillRoundRect(x + 1, y + 1, 10, 10, 3, 3)
            g2.color = JBColor(0xBBBBBB, 0x555555)
            g2.drawRoundRect(x + 1, y + 1, 10, 10, 3, 3)
        } finally {
            g2.dispose()
        }
    }
}
