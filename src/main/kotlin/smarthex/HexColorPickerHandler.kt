package smarthex

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.ui.ColorChooser
import java.awt.Color

/**
 * Handles the click-to-edit flow: open the platform color chooser, then replace the matched
 * `#XXXXXX` range in the document with the newly chosen color, keeping the surrounding
 * wrapper characters intact. The replacement runs inside a WriteCommandAction so undo works.
 */
object HexColorPickerHandler {

    private val HEX_REGEX = Regex("#[0-9A-Fa-f]{6}")

    fun chooseAndReplace(project: Project, editor: Editor, highlighter: RangeHighlighter) {
        val document = editor.document
        val start = highlighter.startOffset
        val end = highlighter.endOffset
        if (start < 0 || end > document.textLength) return

        val current = document.getText(TextRange(start, end))
        val initial = HexHighlightingService.parseColor(current) ?: return

        // ColorChooser.chooseColor(parent, caption, color, withOpacity) -> Color?
        val chosen = ColorChooser.chooseColor(editor.component, "Choose Color", initial, false) ?: return
        replaceWithColor(project, editor, highlighter, chosen)
    }

    private fun replaceWithColor(
        project: Project,
        editor: Editor,
        highlighter: RangeHighlighter,
        color: Color
    ) {
        val newHex = "#" + toHex(color)
        WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
            val document = editor.document
            val s = highlighter.startOffset
            val e = highlighter.endOffset
            if (s < 0 || e > document.textLength) return@run
            val current = document.getText(TextRange(s, e))
            if (!current.matches(HEX_REGEX)) return@run
            document.replaceString(s, e, newHex)
        }
        // The document change triggers the service's listener, which re-renders the block.
    }

    private fun toHex(c: Color): String =
        "%02X%02X%02X".format(c.red, c.green, c.blue)
}
