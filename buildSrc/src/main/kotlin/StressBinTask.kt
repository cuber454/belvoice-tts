import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import ru.kost.ruvoice.bin.StressBin

/** assets/silero/stress.bin из silero_ru.json и eyo_safe.txt (см. StressBin). */
abstract class StressBinTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val json: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val eyo: RegularFileProperty
    @get:OutputDirectory abstract val outDir: DirectoryProperty

    @TaskAction fun run() {
        val f = outDir.get().file("silero/stress.bin").asFile
        f.parentFile.mkdirs()
        f.writeBytes(StressBin.write(json.get().asFile.readText(), eyo.get().asFile.readLines().asSequence()))
    }
}
