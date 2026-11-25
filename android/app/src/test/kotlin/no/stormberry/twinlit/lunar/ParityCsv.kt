package no.stormberry.twinlit.lunar

/** The data rows of a comma-separated corpus in src/test/resources, comment lines left out. */
internal fun parityRows(resource: String): List<List<String>> =
    ParityCsvAnchor::class.java.classLoader!!.getResourceAsStream(resource)!!
        .bufferedReader().useLines { lines ->
            lines.filterNot { it.isBlank() || it.startsWith("#") }.map { it.split(",") }.toList()
        }

private object ParityCsvAnchor
