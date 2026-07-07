package com.hazzus.karooclimber

import com.hazzus.karooclimber.overlay.ViewModeMachine
import com.hazzus.karooclimber.overlay.ViewModeMachine.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModeMachineTest {

    private val windows = listOf(2000.0, 5000.0, 10000.0)

    @Test
    fun `cycles base through all applicable windows and back`() {
        val m = ViewModeMachine()
        val remaining = 12000.0
        assertEquals(Mode.Base, m.mode)
        m.next(false, windows, remaining)
        assertEquals(Mode.Preview(2000.0), m.mode)
        m.next(false, windows, remaining)
        assertEquals(Mode.Preview(5000.0), m.mode)
        m.next(false, windows, remaining)
        assertEquals(Mode.Preview(10000.0), m.mode)
        m.next(false, windows, remaining)
        assertEquals(Mode.Base, m.mode)
    }

    @Test
    fun `both mode inserts alt after base`() {
        val m = ViewModeMachine()
        val remaining = 12000.0
        m.next(true, windows, remaining)
        assertEquals(Mode.Alt, m.mode)
        m.next(true, windows, remaining)
        assertEquals(Mode.Preview(2000.0), m.mode)
        m.next(true, windows, remaining)
        assertEquals(Mode.Preview(5000.0), m.mode)
        m.next(true, windows, remaining)
        assertEquals(Mode.Preview(10000.0), m.mode)
        m.next(true, windows, remaining)
        assertEquals(Mode.Base, m.mode)
    }

    @Test
    fun `both mode with no applicable window cycles base-alt-base`() {
        val m = ViewModeMachine()
        m.next(true, windows, 1500.0)
        assertEquals(Mode.Alt, m.mode)
        m.next(true, windows, 1500.0)
        assertEquals(Mode.Base, m.mode)
    }

    @Test
    fun `skips windows larger than remaining`() {
        val m = ViewModeMachine()
        val remaining = 4000.0 // only the 2 km window applies
        m.next(false, windows, remaining)
        assertEquals(Mode.Preview(2000.0), m.mode)
        m.next(false, windows, remaining)
        assertEquals(Mode.Base, m.mode)
    }

    @Test
    fun `no applicable window keeps base`() {
        val m = ViewModeMachine()
        m.next(false, windows, 1500.0) // less than smallest window
        assertEquals(Mode.Base, m.mode)
    }

    @Test
    fun `auto-reverts to base when remaining drops below active window`() {
        val m = ViewModeMachine()
        m.next(false, windows, 12000.0)
        m.next(false, windows, 12000.0) // Preview(5000)
        assertEquals(Mode.Preview(5000.0), m.mode)
        m.onProgress(6000.0) // still > 5000, stays
        assertEquals(Mode.Preview(5000.0), m.mode)
        m.onProgress(4900.0) // dropped below window
        assertEquals(Mode.Base, m.mode)
    }

    @Test
    fun `skip mid-cycle when remaining shrank between taps`() {
        val m = ViewModeMachine()
        m.next(false, windows, 12000.0) // Preview(2000)
        // remaining now 4500: next applicable after 2000 is none (5000/10000 too big)
        m.next(false, windows, 4500.0)
        assertEquals(Mode.Base, m.mode)
    }

    @Test
    fun `size transitions step chip-expanded-full`() {
        val m = ViewModeMachine()
        assertEquals(ViewModeMachine.PanelSize.CHIP, m.size)
        m.expand()
        assertEquals(ViewModeMachine.PanelSize.EXPANDED, m.size)
        m.expand()
        assertEquals(ViewModeMachine.PanelSize.FULL, m.size)
        m.expand() // stays full
        assertEquals(ViewModeMachine.PanelSize.FULL, m.size)
        m.collapse()
        assertEquals(ViewModeMachine.PanelSize.EXPANDED, m.size)
        m.collapse()
        assertEquals(ViewModeMachine.PanelSize.CHIP, m.size)
        m.collapse() // stays chip
        assertEquals(ViewModeMachine.PanelSize.CHIP, m.size)
    }

    @Test
    fun `new climb pops drawer and climb end returns to chip`() {
        val m = ViewModeMachine()
        m.next(false, windows, 12000.0)
        m.onNewClimb()
        assertEquals(ViewModeMachine.PanelSize.EXPANDED, m.size)
        assertEquals(Mode.Base, m.mode)
        m.expand() // rider went full screen
        m.next(false, windows, 12000.0)
        m.onClimbEnded()
        assertEquals(ViewModeMachine.PanelSize.CHIP, m.size)
        assertEquals(Mode.Base, m.mode)
    }
}
