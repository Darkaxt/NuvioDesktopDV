package com.nuvio.app.features.player.desktop

import java.awt.Insets
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopMaximizedBoundsTest {
    @Test
    fun `maximized outer bounds place the invisible resize frame outside the work area`() {
        val workArea = Rectangle(0, 0, 3413, 1392)
        val frameInsets = Insets(31, 8, 8, 8)

        assertEquals(
            Rectangle(-8, -8, 3429, 1408),
            maximizedOuterBounds(workArea, frameInsets),
        )
    }

    @Test
    fun `maximized outer bounds preserve an offset monitor work area and taskbar reservation`() {
        val workArea = Rectangle(-2560, 24, 2560, 1416)
        val frameInsets = Insets(30, 7, 7, 7)

        assertEquals(
            Rectangle(-2567, 17, 2574, 1430),
            maximizedOuterBounds(workArea, frameInsets),
        )
    }
}
