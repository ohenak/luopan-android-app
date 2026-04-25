// Test that parses the COF file from the filesystem path directly
// (not from Android resources — that's an instrumented test)
// Verifies:
// 1. The file exists at the expected path
// 2. The first coefficient line has n=1, m=0
// 3. The file contains at least 90 coefficient lines (the sentinel line terminates the data)

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class Wmm2025CofResourceTest {
    private val cofFile = File("src/main/res/raw/wmm2025_cof.txt")

    @Test
    fun `cof file exists`() {
        assertTrue("wmm2025_cof.txt not found", cofFile.exists())
    }

    @Test
    fun `cof file has header and coefficient lines`() {
        val lines = cofFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue("Expected at least 90 data lines", lines.size >= 90)
    }

    @Test
    fun `first coefficient line is n=1 m=0`() {
        val lines = cofFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val firstCoeff = lines[1].trim().split("\\s+".toRegex()) // index 0 is header
        assertEquals("1", firstCoeff[0])
        assertEquals("0", firstCoeff[1])
    }
}
