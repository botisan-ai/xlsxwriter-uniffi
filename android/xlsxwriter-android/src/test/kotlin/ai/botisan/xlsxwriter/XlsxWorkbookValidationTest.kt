package ai.botisan.xlsxwriter

import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class XlsxWorkbookValidationTest {
    @Test
    fun writeDateRejectsYearThatWouldWrapAcrossNativeBoundary() {
        XlsxWorkbook().use { workbook ->
            val sheet = workbook.addWorksheet("Dates")

            assertThrows(IllegalArgumentException::class.java) {
                workbook.writeDate(
                    sheet,
                    row = 0,
                    column = 0,
                    value = LocalDate.of(67_440, 1, 1),
                )
            }
        }
    }
}
