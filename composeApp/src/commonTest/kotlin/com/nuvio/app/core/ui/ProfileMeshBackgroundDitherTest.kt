package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class ProfileMeshBackgroundDitherTest {
    @Test
    fun ditherTileIsBalancedAndHighFrequency() {
        val size = 64
        val levels = List(size) { y ->
            List(size) { x -> profileMeshDitherLevel(x, y) }
        }
        val raisedPixels = levels.sumOf { row -> row.sum() }
        val horizontalTransitions = levels.sumOf { row ->
            row.zipWithNext().count { (left, right) -> left != right }
        }
        val distinctRows = levels.map { row -> row.joinToString(separator = "") }.toSet().size

        assertTrue(raisedPixels in 1_800..2_300, "Dither must stay close to a half-tone distribution")
        assertTrue(horizontalTransitions >= 1_500, "Dither must break long horizontal quantization bands")
        assertTrue(distinctRows >= 60, "Dither must not repeat as a visible row pattern")
    }
}
