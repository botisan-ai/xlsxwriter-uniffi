package ai.botisan.xlsxwriter

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class XlsxWorkbookRoundTripTest {
    @Test
    fun workbookValuesSurviveAnXlsxRoundTrip() {
        val output = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "round-trip.xlsx")

        XlsxWorkbook().use { workbook ->
            val sheet = workbook.addWorksheet("Receipts")
            workbook.writeString(sheet, row = 0, column = 0, value = "Merchant")
            workbook.writeInteger(sheet, row = 1, column = 0, value = 7)
            workbook.writeNumber(sheet, row = 1, column = 1, value = 12.34)
            workbook.writeDate(sheet, row = 1, column = 2, value = ExcelDate(2026, 8, 3))
            workbook.setColumnWidth(sheet, column = 0, width = 18.0)
            workbook.save(output)
        }

        XlsxArchive(output).use { archive ->
            assertEquals(listOf("Receipts"), archive.sheetNames())
            assertEquals("Merchant", archive.stringCell("A1"))
            assertEquals("7", archive.cell("A2").value)
            assertEquals("12.34", archive.cell("B2").value)
            assertEquals("46237", archive.cell("C2").value)
            assertNotNull(archive.cell("C2").style)
            assertTrue(archive.columnWidth(1) >= 18.0)
        }
    }
}

private data class XlsxCell(
    val value: String,
    val type: String?,
    val style: String?,
)

private class XlsxArchive(
    file: File,
) : AutoCloseable {
    private val zip = ZipFile(file)

    fun sheetNames(): List<String> =
        document("xl/workbook.xml")
            .getElementsByTagNameNS("*", "sheet")
            .let { sheets ->
                (0 until sheets.length).map { index ->
                    (sheets.item(index) as Element).getAttribute("name")
                }
            }

    fun stringCell(reference: String): String {
        val sharedStringIndex = cell(reference).value.toInt()
        val sharedStrings = document("xl/sharedStrings.xml").getElementsByTagNameNS("*", "si")
        return sharedStrings.item(sharedStringIndex).textContent
    }

    fun cell(reference: String): XlsxCell {
        val cells = document("xl/worksheets/sheet1.xml").getElementsByTagNameNS("*", "c")
        val cell =
            (0 until cells.length)
                .map { cells.item(it) as Element }
                .single { it.getAttribute("r") == reference }
        val value = cell.getElementsByTagNameNS("*", "v").item(0).textContent
        return XlsxCell(
            value = value,
            type = cell.getAttribute("t").ifEmpty { null },
            style = cell.getAttribute("s").ifEmpty { null },
        )
    }

    fun columnWidth(oneBasedColumn: Int): Double {
        val columns = document("xl/worksheets/sheet1.xml").getElementsByTagNameNS("*", "col")
        val column =
            (0 until columns.length)
                .map { columns.item(it) as Element }
                .single { it.getAttribute("min").toInt() == oneBasedColumn }
        return column.getAttribute("width").toDouble()
    }

    private fun document(path: String): Document {
        val entry = requireNotNull(zip.getEntry(path)) { "Missing XLSX part: $path" }
        return zip.getInputStream(entry).use { stream ->
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(stream)
        }
    }

    override fun close() {
        zip.close()
    }
}
