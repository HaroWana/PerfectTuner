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
        shortName = "Chr",
        strings = emptyList(),
        description = "Detects any note in the 12-tone scale"
    )

    val FREE_TUNING_IDS = setOf(
        "chromatic", "standard", "eb_standard", "drop_d", "open_g", "open_d", "dadgad"
    )

    fun isFree(tuningId: String): Boolean = tuningId in FREE_TUNING_IDS

    private data class StringSpec(val noteName: String, val octave: Int, val midiNumber: Int)

    private fun s(noteName: String, octave: Int, midiNumber: Int) =
        StringSpec(noteName, octave, midiNumber)

    private fun strings(vararg specs: StringSpec): List<GuitarString> =
        specs.mapIndexed { index, spec ->
            GuitarString(index, spec.noteName, spec.octave, spec.midiNumber)
        }

    // ── Common ──────────────────────────────────────────────────────────────
    // Strings listed low to high (string 6 to string 1)
    private val commonTunings: List<GuitarTuning> = listOf(
        CHROMATIC_MODE,
        STANDARD_TUNING,  // E2(40) A2(45) D3(50) G3(55) B3(59) E4(64)
        GuitarTuning(
            id = "eb_standard", name = "Eb Standard", shortName = "Eb",
            strings = strings(s("Eb", 2, 39), s("Ab", 2, 44), s("Db", 3, 49), s("Gb", 3, 54), s("Bb", 3, 58), s("Eb", 4, 63))
        ),
        GuitarTuning(
            id = "d_standard", name = "D Standard", shortName = "D Std",
            strings = strings(s("D", 2, 38), s("G", 2, 43), s("C", 3, 48), s("F", 3, 53), s("A", 3, 57), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "c_standard", name = "C Standard",
            strings = strings(s("C", 2, 36), s("F", 2, 41), s("Bb", 2, 46), s("Eb", 3, 51), s("G", 3, 55), s("C", 4, 60))
        ),
        GuitarTuning(
            id = "open_g", name = "Open G",
            strings = strings(s("D", 2, 38), s("G", 2, 43), s("D", 3, 50), s("G", 3, 55), s("B", 3, 59), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "open_d", name = "Open D",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("F#", 3, 54), s("A", 3, 57), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "open_e", name = "Open E",
            strings = strings(s("E", 2, 40), s("B", 2, 47), s("E", 3, 52), s("G#", 3, 56), s("B", 3, 59), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "open_a", name = "Open A",
            strings = strings(s("E", 2, 40), s("A", 2, 45), s("E", 3, 52), s("A", 3, 57), s("C#", 4, 61), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "open_c", name = "Open C",
            strings = strings(s("C", 2, 36), s("G", 2, 43), s("C", 3, 48), s("G", 3, 55), s("C", 4, 60), s("E", 4, 64))
        )
    )

    // ── Drop ────────────────────────────────────────────────────────────────
    private val dropTunings: List<GuitarTuning> = listOf(
        GuitarTuning(
            id = "drop_d", name = "Drop D", shortName = "Drop D",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("G", 3, 55), s("B", 3, 59), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "drop_c", name = "Drop C", shortName = "Drop C",
            strings = strings(s("C", 2, 36), s("G", 2, 43), s("C", 3, 48), s("F", 3, 53), s("A", 3, 57), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "drop_b", name = "Drop B",
            strings = strings(s("B", 1, 35), s("F#", 2, 42), s("B", 2, 47), s("E", 3, 52), s("G#", 3, 56), s("C#", 4, 61))
        ),
        GuitarTuning(
            id = "drop_a", name = "Drop A",
            strings = strings(s("A", 1, 33), s("E", 2, 40), s("A", 2, 45), s("D", 3, 50), s("F#", 3, 54), s("B", 3, 59))
        ),
        GuitarTuning(
            id = "double_drop_d", name = "Double Drop D",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("G", 3, 55), s("B", 3, 59), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "dadgad", name = "DADGAD",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("G", 3, 55), s("A", 3, 57), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "open_d_minor", name = "Open D Minor",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("F", 3, 53), s("A", 3, 57), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "open_g_minor", name = "Open G Minor",
            strings = strings(s("D", 2, 38), s("G", 2, 43), s("D", 3, 50), s("G", 3, 55), s("Bb", 3, 58), s("D", 4, 62))
        )
    )

    // ── Slide / Resonator ───────────────────────────────────────────────────
    private val slideTunings: List<GuitarTuning> = listOf(
        GuitarTuning(
            id = "open_e_minor", name = "Open E Minor",
            strings = strings(s("E", 2, 40), s("B", 2, 47), s("E", 3, 52), s("G", 3, 55), s("B", 3, 59), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "open_a_minor", name = "Open A Minor",
            strings = strings(s("E", 2, 40), s("A", 2, 45), s("E", 3, 52), s("A", 3, 57), s("C", 4, 60), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "open_c_minor", name = "Open C Minor",
            strings = strings(s("C", 2, 36), s("G", 2, 43), s("C", 3, 48), s("G", 3, 55), s("C", 4, 60), s("Eb", 4, 63))
        ),
        GuitarTuning(
            id = "vestapol", name = "Vestapol",
            description = "Traditional slide name for Open D (D A D F# A D)",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("F#", 3, 54), s("A", 3, 57), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "spanish", name = "Spanish",
            description = "Traditional slide name for Open G (D G D G B D)",
            strings = strings(s("D", 2, 38), s("G", 2, 43), s("D", 3, 50), s("G", 3, 55), s("B", 3, 59), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "dobro_g", name = "Dobro / Lap Steel G",
            strings = strings(s("G", 2, 43), s("B", 2, 47), s("D", 3, 50), s("G", 3, 55), s("B", 3, 59), s("D", 4, 62))
        )
    )

    // ── Extended / Exotic ───────────────────────────────────────────────────
    private val exoticTunings: List<GuitarTuning> = listOf(
        GuitarTuning(
            id = "all_fourths", name = "All Fourths",
            strings = strings(s("E", 2, 40), s("A", 2, 45), s("D", 3, 50), s("G", 3, 55), s("C", 4, 60), s("F", 4, 65))
        ),
        GuitarTuning(
            id = "nst", name = "New Standard (NST)",
            description = "Robert Fripp's tuning",
            strings = strings(s("C", 2, 36), s("G", 2, 43), s("D", 3, 50), s("A", 3, 57), s("E", 4, 64), s("G", 4, 67))
        ),
        GuitarTuning(
            id = "open_c6", name = "Open C6",
            strings = strings(s("C", 2, 36), s("A", 2, 45), s("C", 3, 48), s("G", 3, 55), s("C", 4, 60), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "open_b", name = "Open B",
            strings = strings(s("B", 1, 35), s("F#", 2, 42), s("B", 2, 47), s("D#", 3, 51), s("F#", 3, 54), s("B", 3, 59))
        ),
        GuitarTuning(
            id = "nashville", name = "Nashville",
            description = "High-strung standard: wound strings tuned an octave up",
            strings = strings(s("E", 3, 52), s("A", 3, 57), s("D", 4, 62), s("G", 4, 67), s("B", 3, 59), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "cgdgbd", name = "CGDGBD",
            description = "Robert Johnson / Delta blues",
            strings = strings(s("C", 2, 36), s("G", 2, 43), s("D", 3, 50), s("G", 3, 55), s("B", 3, 59), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "dadead", name = "DADEAD",
            description = "Celtic cross tuning",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("E", 3, 52), s("A", 3, 57), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "eeeebe", name = "EEEEBE",
            description = "Sonic Youth-style (approximate octave layout)",
            strings = strings(s("E", 2, 40), s("E", 3, 52), s("E", 3, 52), s("E", 3, 52), s("B", 3, 59), s("E", 4, 64))
        ),
        GuitarTuning(
            id = "cgcggc", name = "CGCGGC",
            description = "Joni Mitchell — The Hissing of Summer Lawns",
            strings = strings(s("C", 2, 36), s("G", 2, 43), s("C", 3, 48), s("G", 3, 55), s("G", 3, 55), s("C", 4, 60))
        ),
        GuitarTuning(
            id = "cgdgcd", name = "CGDGCD",
            strings = strings(s("C", 2, 36), s("G", 2, 43), s("D", 3, 50), s("G", 3, 55), s("C", 4, 60), s("D", 4, 62))
        ),
        GuitarTuning(
            id = "daddad", name = "DADDAD",
            strings = strings(s("D", 2, 38), s("A", 2, 45), s("D", 3, 50), s("D", 3, 50), s("A", 3, 57), s("D", 4, 62))
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
