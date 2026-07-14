package smarthex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import java.awt.Color
import java.util.regex.Pattern

/**
 * Project-level service that maintains inline color-block highlights and gutter indicators for
 * every open editor belonging to the project. Works purely on the editor Document layer (regex
 * matching) and does not depend on the PSI tree, so it works in any file type.
 */
class HexHighlightingService(private val project: Project) : Disposable {

    private data class HexMatch(val start: Int, val end: Int, val color: Color)

    private class EditorState {
        val disposable: Disposable = Disposer.newDisposable()
        val highlighters = mutableListOf<RangeHighlighter>()
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
    }

    private val states = mutableMapOf<Editor, EditorState>()

    @Volatile
    private var cacheVersion: Long = -1L

    @Volatile
    private var cachedPatterns: List<Pattern> = emptyList()

    init {
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project === project) attach(editor)
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                detach(event.editor)
            }
        }, this)
    }

    /** Attach to an editor (idempotent). */
    fun attach(editor: Editor) {
        if (editor.project !== project) return
        synchronized(states) {
            if (states.containsKey(editor)) return
            val state = EditorState()
            states[editor] = state
            editor.document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    state.alarm.cancelAllRequests()
                    state.alarm.addRequest({ refresh(editor) }, 250)
                }
            }, state.disposable)
        }
        refresh(editor)
    }

    /** Attach to all currently-open editors for this project (used at startup). */
    fun attachExistingEditors() {
        EditorFactory.getInstance().allEditors.forEach { editor ->
            if (editor.project === project) attach(editor)
        }
    }

    private fun detach(editor: Editor) {
        val state = synchronized(states) { states.remove(editor) } ?: return
        val markup = editor.markupModel
        state.highlighters.forEach { runCatching { if (it.isValid) markup.removeHighlighter(it) } }
        state.highlighters.clear()
        runCatching { Disposer.dispose(state.disposable) }
    }

    private fun compiledPatterns(): List<Pattern> {
        val cfg = HexPatternConfig.getInstance()
        if (cfg.version != cacheVersion) {
            cacheVersion = cfg.version
            cachedPatterns = cfg.patterns.mapNotNull { runCatching { Pattern.compile(it) }.getOrNull() }
        }
        return cachedPatterns
    }

    fun refresh(editor: Editor) {
        ReadAction.run<RuntimeException> {
            doRefresh(editor)
        }
    }

    private fun doRefresh(editor: Editor) {
        val state = synchronized(states) { states[editor] } ?: return
        val document = editor.document
        val markup = editor.markupModel

        state.highlighters.forEach { runCatching { if (it.isValid) markup.removeHighlighter(it) } }
        state.highlighters.clear()

        val text = document.text
        if (text.isEmpty()) return

        // Collect all matches across all configured patterns.
        val matches = ArrayList<HexMatch>()
        for (pattern in compiledPatterns()) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val group = if (matcher.groupCount() >= 1 && matcher.start(1) >= 0) 1 else 0
                val raw = matcher.group(group) ?: continue
                val matchStart: Int
                val matchEnd: Int
                val hex: String
                if (group == 0) {
                    // No capturing group — try to find #XXXXXX within the full match.
                    // This handles patterns like \[#[0-9A-Fa-f]{6}\] without a capture group.
                    val found = HEX_REGEX.find(raw) ?: continue
                    hex = found.value
                    matchStart = matcher.start(group) + found.range.first
                    matchEnd = matcher.start(group) + found.range.last + 1
                } else {
                    hex = raw
                    matchStart = matcher.start(group)
                    matchEnd = matcher.end(group)
                }
                if (!hex.matches(HEX_REGEX)) continue
                val color = parseColor(hex) ?: continue
                matches.add(HexMatch(matchStart, matchEnd, color))
            }
        }

        // Sort and drop overlaps so each range is highlighted once.
        matches.sortBy { it.start }
        var lastEnd = -1
        for (match in matches) {
            if (match.start < lastEnd) continue
            lastEnd = match.end

            val attributes = TextAttributes()
            attributes.backgroundColor = match.color
            attributes.foregroundColor = if (luminance(match.color) > 150) Color.BLACK else Color.WHITE

            val highlighter = markup.addRangeHighlighter(
                match.start, match.end,
                HighlighterLayer.SELECTION - 1,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
            )
            highlighter.setGutterIconRenderer(
                HexGutterProvider.createRenderer(project, editor, highlighter, match.color)
            )
            state.highlighters.add(highlighter)
        }
    }

    fun refreshAll() {
        val editors: List<Editor>
        synchronized(states) { editors = states.keys.toList() }
        editors.forEach { refresh(it) }
    }

    override fun dispose() {
        val editors: List<Editor>
        synchronized(states) { editors = ArrayList(states.keys) }
        editors.forEach { detach(it) }
    }

    companion object {
        private val HEX_REGEX = Regex("#[0-9A-Fa-f]{6}")

        fun getInstance(project: Project): HexHighlightingService =
            project.getService(HexHighlightingService::class.java)

        fun parseColor(hex: String): Color? {
            if (!hex.matches(HEX_REGEX)) return null
            val v = hex.substring(1).toInt(16)
            return Color((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        }

        fun luminance(c: Color): Int =
            (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue).toInt()
    }
}
