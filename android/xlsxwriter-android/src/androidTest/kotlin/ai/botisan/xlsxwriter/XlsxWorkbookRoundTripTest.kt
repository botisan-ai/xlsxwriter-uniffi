package ai.botisan.xlsxwriter

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.ReadingOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class XlsxWorkbookRoundTripTest {
    @Test
    fun workbookValuesSurviveAnXlsxRoundTrip() {
        val workbookBytes =
            XlsxWorkbook().use { workbook ->
                val sheet = workbook.addWorksheet("Receipts")
                workbook.writeString(sheet, row = 0, column = 0, value = "Merchant")
                workbook.writeInteger(sheet, row = 1, column = 0, value = 7)
                workbook.writeNumber(sheet, row = 1, column = 1, value = 12.34)
                workbook.writeDate(sheet, row = 1, column = 2, value = LocalDate.of(2026, 8, 3))
                workbook.setColumnWidth(sheet, column = 0, width = 18.0)
                workbook.saveToByteArray()
            }

        ByteArrayInputStream(workbookBytes).use { input ->
            ReadableWorkbook(input, ReadingOptions(true, true)).use { workbook ->
                assertEquals(listOf("Receipts"), workbook.sheets.map { it.name }.toList())

                val rows = workbook.firstSheet.read()
                assertEquals(CellType.STRING, rows[0].getCell(0).type)
                assertEquals("Merchant", rows[0].getCell(0).asString())
                assertEquals(CellType.NUMBER, rows[1].getCell(0).type)
                assertEquals(BigDecimal("7"), rows[1].getCell(0).asNumber())
                assertEquals(CellType.NUMBER, rows[1].getCell(1).type)
                assertEquals(BigDecimal("12.34"), rows[1].getCell(1).asNumber())
                assertEquals(CellType.NUMBER, rows[1].getCell(2).type)
                assertEquals(LocalDate.of(2026, 8, 3), rows[1].getCell(2).asDate().toLocalDate())
                assertEquals("yyyy-mm-dd", rows[1].getCell(2).dataFormatString)
            }
        }
    }
}
