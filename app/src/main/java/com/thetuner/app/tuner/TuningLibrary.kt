package com.thetuner.app.tuner

object TuningLibrary {

    data class TuningSection(
        val title: String,
        val tunings: List<GuitarTuning>
    )

    // Chromatic is a special mode: empty strings list signals "detect any note"
    val CHROMATIC_MODE = GuitarTuning(
        id = "chromatic",
        name = "Chromatic",
        strings = emptyList(),
        description = "Detects any note in the 12-tone scale"
    )

    val FREE_TUNING_IDS = setOf(
        "standard", "eb_standard", "drop_d", "open_g", "open_d", "dadgad"
    )

    fun isFree(tuningId: String): Boolean = tuningId in FREE_TUNING_IDS

    // Helper: build a GuitarString list from flat (noteName, octave, midiNumber) triples
    private fun strings(vararg data: Any): List<GuitarString> {
        val result = mutableListOf<GuitarString>()
        var i = 0
        var idx = 0
        while (i < data.size) {
            result.add(GuitarString(idx, data[i] as String, data[i + 1] as Int, data[i + 2] as Int))
            i += 3
            idx++
        }
        return result
    }

    // ── Common ──────────────────────────────────────────────────────────────
    // Strings listed low to high (string 6 to string 1)
    private val commonTunings: List<GuitarTuning> = listOf(
        CHROMATIC_MODE,
        STANDARD_TUNING,  // E2(40) A2(45) D3(50) G3(55) B3(59) E4(64)
        GuitarTuning(
            id = "eb_standard", name = "Eb Standard",
            strings = strings("Eb", 2, 39, "Ab", 2, 44, "Db", 3, 49, "Gb", 3, 54, "Bb", 3, 58, "Eb", 4, 63)
        ),
        GuitarTuning(
            id = "d_standard", name = "D Standard",
            strings = strings("D", 2, 38, "G", 2, 43, "C", 3, 48, "F", 3, 53, "A", 3, 57, "D", 4, 62)
        ),
        GuitarTuning(
            id = "c_standard", name = "C Standard",
            strings = strings("C", 2, 36, "F", 2, 41, "Bb", 2, 46, "Eb", 3, 51, "G", 3, 55, "C", 4, 60)
        ),
        GuitarTuning(
            id = "open_g", name = "Open G",
            strings = strings("D", 2, 38, "G", 2, 43, "D", 3, 50, "G", 3, 55, "B", 3, 59, "D", 4, 62)
        ),
        GuitarTuning(
            id = "open_d", name = "Open D",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "F#", 3, 54, "A", 3, 57, "D", 4, 62)
        ),
        GuitarTuning(
            id = "open_e", name = "Open E",
            strings = strings("E", 2, 40, "B", 2, 47, "E", 3, 52, "G#", 3, 56, "B", 3, 59, "E", 4, 64)
        ),
        GuitarTuning(
            id = "open_a", name = "Open A",
            strings = strings("E", 2, 40, "A", 2, 45, "E", 3, 52, "A", 3, 57, "C#", 4, 61, "E", 4, 64)
        ),
        GuitarTuning(
            id = "open_c", name = "Open C",
            strings = strings("C", 2, 36, "G", 2, 43, "C", 3, 48, "G", 3, 55, "C", 4, 60, "E", 4, 64)
        )
    )

    // ── Drop ────────────────────────────────────────────────────────────────
    private val dropTunings: List<GuitarTuning> = listOf(
        GuitarTuning(
            id = "drop_d", name = "Drop D",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "G", 3, 55, "B", 3, 59, "E", 4, 64)
        ),
        GuitarTuning(
            id = "drop_c", name = "Drop C",
            strings = strings("C", 2, 36, "G", 2, 43, "C", 3, 48, "F", 3, 53, "A", 3, 57, "D", 4, 62)
        ),
        GuitarTuning(
            id = "drop_b", name = "Drop B",
            strings = strings("B", 1, 35, "F#", 2, 42, "B", 2, 47, "E", 3, 52, "G#", 3, 56, "C#", 4, 61)
        ),
        GuitarTuning(
            id = "drop_a", name = "Drop A",
            strings = strings("A", 1, 33, "E", 2, 40, "A", 2, 45, "D", 3, 50, "F#", 3, 54, "B", 3, 59)
        ),
        GuitarTuning(
            id = "double_drop_d", name = "Double Drop D",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "G", 3, 55, "B", 3, 59, "D", 4, 62)
        ),
        GuitarTuning(
            id = "dadgad", name = "DADGAD",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "G", 3, 55, "A", 3, 57, "D", 4, 62)
        ),
        GuitarTuning(
            id = "open_d_minor", name = "Open D Minor",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "F", 3, 53, "A", 3, 57, "D", 4, 62)
        ),
        GuitarTuning(
            id = "open_g_minor", name = "Open G Minor",
            strings = strings("D", 2, 38, "G", 2, 43, "D", 3, 50, "G", 3, 55, "Bb", 3, 58, "D", 4, 62)
        )
    )

    // ── Slide / Resonator ───────────────────────────────────────────────────
    private val slideTunings: List<GuitarTuning> = listOf(
        GuitarTuning(
            id = "open_e_minor", name = "Open E Minor",
            strings = strings("E", 2, 40, "B", 2, 47, "E", 3, 52, "G", 3, 55, "B", 3, 59, "E", 4, 64)
        ),
        GuitarTuning(
            id = "open_a_minor", name = "Open A Minor",
            strings = strings("E", 2, 40, "A", 2, 45, "E", 3, 52, "A", 3, 57, "C", 4, 60, "E", 4, 64)
        ),
        GuitarTuning(
            id = "open_c_minor", name = "Open C Minor",
            strings = strings("C", 2, 36, "G", 2, 43, "C", 3, 48, "G", 3, 55, "C", 4, 60, "Eb", 4, 63)
        ),
        GuitarTuning(
            id = "vestapol", name = "Vestapol",
            description = "Traditional slide name for Open D (D A D F# A D)",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "F#", 3, 54, "A", 3, 57, "D", 4, 62)
        ),
        GuitarTuning(
            id = "spanish", name = "Spanish",
            description = "Traditional slide name for Open G (D G D G B D)",
            strings = strings("D", 2, 38, "G", 2, 43, "D", 3, 50, "G", 3, 55, "B", 3, 59, "D", 4, 62)
        ),
        GuitarTuning(
            id = "dobro_g", name = "Dobro / Lap Steel G",
            strings = strings("G", 2, 43, "B", 2, 47, "D", 3, 50, "G", 3, 55, "B", 3, 59, "D", 4, 62)
        )
    )

    // ── Extended / Exotic ───────────────────────────────────────────────────
    private val exoticTunings: List<GuitarTuning> = listOf(
        GuitarTuning(
            id = "all_fourths", name = "All Fourths",
            strings = strings("E", 2, 40, "A", 2, 45, "D", 3, 50, "G", 3, 55, "C", 4, 60, "F", 4, 65)
        ),
        GuitarTuning(
            id = "nst", name = "New Standard (NST)",
            description = "Robert Fripp's tuning",
            strings = strings("C", 2, 36, "G", 2, 43, "D", 3, 50, "A", 3, 57, "E", 4, 64, "G", 4, 67)
        ),
        GuitarTuning(
            id = "open_c6", name = "Open C6",
            strings = strings("C", 2, 36, "A", 2, 45, "C", 3, 48, "G", 3, 55, "C", 4, 60, "E", 4, 64)
        ),
        GuitarTuning(
            id = "open_b", name = "Open B",
            strings = strings("B", 1, 35, "F#", 2, 42, "B", 2, 47, "D#", 3, 51, "F#", 3, 54, "B", 3, 59)
        ),
        GuitarTuning(
            id = "nashville", name = "Nashville",
            description = "High-strung standard: wound strings tuned an octave up",
            strings = strings("E", 3, 52, "A", 3, 57, "D", 4, 62, "G", 4, 67, "B", 3, 59, "E", 4, 64)
        ),
        GuitarTuning(
            id = "cgdgbd", name = "CGDGBD",
            description = "Robert Johnson / Delta blues",
            strings = strings("C", 2, 36, "G", 2, 43, "D", 3, 50, "G", 3, 55, "B", 3, 59, "D", 4, 62)
        ),
        GuitarTuning(
            id = "dadead", name = "DADEAD",
            description = "Celtic cross tuning",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "E", 3, 52, "A", 3, 57, "D", 4, 62)
        ),
        GuitarTuning(
            id = "eeeebe", name = "EEEEBE",
            description = "Sonic Youth-style (approximate octave layout)",
            strings = strings("E", 2, 40, "E", 3, 52, "E", 3, 52, "E", 3, 52, "B", 3, 59, "E", 4, 64)
        ),
        GuitarTuning(
            id = "cgcggc", name = "CGCGGC",
            description = "Joni Mitchell — The Hissing of Summer Lawns",
            strings = strings("C", 2, 36, "G", 2, 43, "C", 3, 48, "G", 3, 55, "G", 3, 55, "C", 4, 60)
        ),
        GuitarTuning(
            id = "cgdgcd", name = "CGDGCD",
            strings = strings("C", 2, 36, "G", 2, 43, "D", 3, 50, "G", 3, 55, "C", 4, 60, "D", 4, 62)
        ),
        GuitarTuning(
            id = "daddad", name = "DADDAD",
            strings = strings("D", 2, 38, "A", 2, 45, "D", 3, 50, "D", 3, 50, "A", 3, 57, "D", 4, 62)
        )
    )

    val sections: List<TuningSection> = listOf(
        TuningSection("Common", commonTunings),
        TuningSection("Drop", dropTunings),
        TuningSection("Slide / Resonator", slideTunings),
        TuningSection("Extended / Exotic", exoticTunings)
    )

    val allTunings: Map<String, GuitarTuning> by lazy {
        sections.flatMap { it.tunings }.associateBy { it.id }
    }

    fun findById(id: String): GuitarTuning = allTunings[id] ?: STANDARD_TUNING
}
