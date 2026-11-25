package no.stormberry.twinlit.widget

import org.junit.Assert.assertNotNull
import java.io.File

/** Files of the repository, found from the module directory the JVM test runs in. */
internal object TestFiles {

    fun find(path: String): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .take(4)
            .map { File(it, path) }
            .firstOrNull { it.exists() }

    fun require(path: String): File {
        val f = find(path)
        // A hard failure, never a skip (fix round 1): a moved file or a run from another working
        // directory must not turn a source or resource check into a green skip.
        assertNotNull("$path not reachable from the test's working directory", f)
        return f!!
    }

    /**
     * A file at the repository root, never one of the same name deeper in the tree: `find("README.md")`
     * returns `android/README.md` from the module directory, because it is nearer.
     */
    fun requireAtRoot(path: String): File {
        val root = require(".gitattributes").parentFile
        val f = File(root, path)
        assertNotNull("$path not at the repository root $root", f.takeIf { it.exists() })
        return f
    }

    const val MAIN = "android/app/src/main"
    const val KOTLIN = "$MAIN/kotlin/no/stormberry/twinlit"
    const val RES = "$MAIN/res"
}
