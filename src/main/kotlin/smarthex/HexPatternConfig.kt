package smarthex

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persistent configuration holding the list of regex patterns used to
 * recognize hex colors. Each pattern should contain a capturing group around the `#XXXXXX`
 * token (group 1). If no usable group is found, the whole match is scanned for `#XXXXXX`.
 */
@State(name = "HexPatternConfig", storages = [Storage("smart-hex-preview.xml")])
class HexPatternConfig : PersistentStateComponent<HexPatternConfig.State> {

    data class State(var patterns: MutableList<String> = ArrayList(defaultPatterns))

    private var myState = State()

    @Volatile
    private var myVersion: Long = 0L

    val version: Long get() = myVersion

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        myVersion++
    }

    val patterns: List<String> get() = myState.patterns

    fun setPatterns(patterns: List<String>) {
        myState = State(patterns.toMutableList())
        myVersion++
    }

    companion object {
        fun getInstance(): HexPatternConfig =
            ApplicationManager.getApplication().getService(HexPatternConfig::class.java)

        /** Default patterns covering [], (), single and double quotes. */
        val defaultPatterns: List<String> = listOf(
            "\\[(#[0-9A-Fa-f]{6})\\]",
            "\\((#[0-9A-Fa-f]{6})\\)",
            "'(#[0-9A-Fa-f]{6})'",
            "\"(#[0-9A-Fa-f]{6})\""
        )
    }
}
