package io.github.wulkanowy.api.attendance

import io.github.wulkanowy.api.BaseLocalTest
import org.junit.Assert.*
import org.junit.Test

class AttendanceTest : BaseLocalTest() {

    private val table by lazy {
        getSnpRepo(AttendanceTest::class.java, "Frekwencja.html").getAttendance(getLocalDate(2018, 10, 1)).blockingGet()
    }

    @Test
    fun getAttendance() {
        assertEquals(7, table.size)
    }

    @Test
    fun getAttendance_presence() {
        table[0].run {
            // wtorek, 1
            assertEquals(1, number)
            assertEquals(getDate(2018, 10, 2), date)
            assertEquals("Zajęcia artystyczne", subject)
            assertEquals("Obecność", name)
            assertTrue(presence)

            assertFalse(absence)
            assertFalse(exemption)
            assertFalse(lateness)
            assertFalse(excused)
            assertFalse(deleted)
        }
    }

    @Test
    fun getAttendance_absence() {
        table[1].run {
            // wtorek, 2
            assertEquals(2, number)
            assertEquals(getDate(2018, 10, 2), date)
            assertEquals("Informatyka", subject)
            assertEquals("Nieobecność nieusprawiedliwiona", name)
            assertTrue(absence)
            assertFalse(excused)

            assertFalse(exemption)
            assertFalse(presence)
            assertFalse(lateness)
            assertFalse(deleted)
        }
    }

    @Test
    fun getAttendance_absenceExcused() {
        table[2].run {
            // środa, 1
            assertEquals(1, number)
            assertEquals(getDate(2018, 10, 3), date)
            assertEquals("Matematyka", subject)
            assertEquals("Nieobecność usprawiedliwiona", name)
            assertTrue(absence)
            assertTrue(excused)

            assertFalse(exemption)
            assertFalse(presence)
            assertFalse(lateness)
            assertFalse(deleted)
        }
    }

    @Test
    fun getAttendance_lateness() {
        table[3].run {
            // środa, 2
            assertEquals(2, number)
            assertEquals(getDate(2018, 10, 3), date)
            assertEquals("Godzina wychowawcza", subject)
            assertEquals("Spóźnienie nieusprawiedliwione", name)
            assertTrue(lateness)
            assertFalse(excused)

            assertFalse(exemption)
            assertFalse(presence)
            assertFalse(absence)
            assertFalse(deleted)
        }
    }

    @Test
    fun getAttendance_latenessExcused() {
        table[4].run {
            // czwartek, 1
            assertEquals(1, number)
            assertEquals(getDate(2018, 10, 4), date)
            assertEquals("Historia", subject)
            assertEquals("Spóźnienie usprawiedliwione", name)
            assertTrue(lateness)
            assertTrue(excused)

            assertFalse(exemption)
            assertFalse(presence)
            assertFalse(absence)
            assertFalse(deleted)
        }
    }

    @Test
    fun getAttendance_absenceForSchoolReason() {
        table[5].run {
            // czwartek, 2
            assertEquals(2, number)
            assertEquals(getDate(2018, 10, 4), date)
            assertEquals("Język angielski", subject)
            assertEquals("Nieobecność z przyczyn szkolnych", name)
            assertTrue(presence)

            assertFalse(excused)
            assertFalse(lateness)
            assertFalse(exemption)
            assertFalse(absence)
            assertFalse(deleted)
        }
    }

    @Test
    fun getAttendance_exemption() {
        table[6].run {
            // piątek, 1
            assertEquals(1, number)
            assertEquals(getDate(2018, 10, 5), date)
            assertEquals("Informatyka", subject)
            assertEquals("Zwolnienie", name)
            assertTrue(exemption)

            assertFalse(lateness)
            assertFalse(excused)
            assertFalse(presence)
            assertFalse(absence)
            assertFalse(deleted)
        }
    }
}
