package ai.botisan.xlsxwriter.consumer

import ai.botisan.xlsxwriter.XlsxWorkbook
import java.io.File
import java.time.LocalDate

internal object ConsumerSmoke {
    fun writeWorkbook(output: File) {
        XlsxWorkbook().use { workbook ->
            val sheet = workbook.addWorksheet("Receipts")
            workbook.writeString(sheet, row = 0, column = 0, value = "Merchant")
            workbook.writeInteger(sheet, row = 1, column = 0, value = 1)
            workbook.writeNumber(sheet, row = 1, column = 1, value = 12.34)
            workbook.writeDate(sheet, row = 1, column = 2, value = LocalDate.of(2026, 8, 3))
            workbook.setColumnWidth(sheet, column = 0, width = 18.0)
            workbook.save(output)
        }
    }
}
