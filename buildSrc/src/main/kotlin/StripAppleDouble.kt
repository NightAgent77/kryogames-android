import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

/** Deletes macOS AppleDouble sidecars so AAPT and D8 do not treat them as build outputs. */
class StripAppleDouble(
    private val root: Provider<Directory>,
) : Action<Task> {
    override fun execute(task: Task) {
        val dir = root.orNull?.asFile ?: return
        if (!dir.exists()) return
        dir.walkTopDown()
            .filter { it.name.startsWith("._") }
            .forEach(File::delete)
    }
}
