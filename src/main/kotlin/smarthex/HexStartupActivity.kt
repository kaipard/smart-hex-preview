package smarthex

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Force-creates the project service at startup and attaches highlighting to editors that
 * were already restored before the service was initialized.
 */
class HexStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        HexHighlightingService.getInstance(project).attachExistingEditors()
    }
}
