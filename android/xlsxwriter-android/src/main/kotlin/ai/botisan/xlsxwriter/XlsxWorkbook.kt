package ai.botisan.xlsxwriter

import java.io.File
import ai.botisan.xlsxwriter.internal.ExcelDate as NativeExcelDate
import ai.botisan.xlsxwriter.internal.ExcelDateTime as NativeExcelDateTime
import ai.botisan.xlsxwriter.internal.Workbook as NativeWorkbook
import ai.botisan.xlsxwriter.internal.XlsxWriterException as NativeXlsxWriterException

private const val MAX_ROW_INDEX = 1_048_575
private const val MAX_COLUMN_INDEX = 16_383

/** A civil date written as an Excel date cell without timezone conversion. */
public data class ExcelDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    init {
        require(year in 1900..9999) { "year must be between 1900 and 9999" }
        require(month in 1..12) { "month must be between 1 and 12" }
        require(day in 1..31) { "day must be between 1 and 31" }
    }

    internal fun toNative(): NativeExcelDate =
        NativeExcelDate(
            year = year.toUShort(),
            month = month.toUByte(),
            day = day.toUByte(),
        )
}

/** A civil date and time written without timezone conversion. */
public data class ExcelDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
) {
    init {
        ExcelDate(year, month, day)
        require(hour in 0..23) { "hour must be between 0 and 23" }
        require(minute in 0..59) { "minute must be between 0 and 59" }
        require(second in 0..59) { "second must be between 0 and 59" }
    }

    internal fun toNative(): NativeExcelDateTime =
        NativeExcelDateTime(
            year = year.toUShort(),
            month = month.toUByte(),
            day = day.toUByte(),
            hour = hour.toUByte(),
            minute = minute.toUByte(),
            second = second.toUByte(),
        )
}

/** A worksheet owned by the workbook that created it. */
public class Worksheet internal constructor(
    internal val nativeIndex: UInt,
    internal val owner: Any,
) {
    val index: Int = nativeIndex.toInt()

    override fun toString(): String = "Worksheet(index=$index)"
}

/** Stable Kotlin error surface for failures reported by the Rust writer. */
public sealed class XlsxWriterException(
    message: String,
    cause: Throwable,
) : Exception(message, cause) {
    class Io internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)

    class RowColumnLimit internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)

    class MaxStringLength internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)

    class WorksheetNameReused internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)

    class InvalidParameter internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)

    class WorksheetNotFound internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)

    class InvalidDate internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)

    class Unknown internal constructor(
        message: String,
        cause: Throwable,
    ) : XlsxWriterException(message, cause)
}

/**
 * Kotlin façade over the Rust-backed XLSX writer.
 *
 * Calls are synchronous. Run large workbook writes from an IO dispatcher or another background thread.
 */
public class XlsxWorkbook private constructor(
    private val native: NativeWorkbook,
) : AutoCloseable {
    private val owner = Any()

    constructor() : this(NativeWorkbook())

    fun addWorksheet(): Worksheet =
        nativeCall {
            Worksheet(native.addWorksheet(), owner)
        }

    fun addWorksheet(name: String): Worksheet =
        nativeCall {
            Worksheet(native.addWorksheetWithName(name), owner)
        }

    fun writeString(
        sheet: Worksheet,
        row: Int,
        column: Int,
        value: String,
    ) {
        nativeCall {
            native.writeString(sheetIndex(sheet), row.toNativeRow(), column.toNativeColumn(), value)
        }
    }

    fun writeInteger(
        sheet: Worksheet,
        row: Int,
        column: Int,
        value: Long,
    ) {
        nativeCall {
            native.writeInteger(sheetIndex(sheet), row.toNativeRow(), column.toNativeColumn(), value)
        }
    }

    fun writeNumber(
        sheet: Worksheet,
        row: Int,
        column: Int,
        value: Double,
    ) {
        nativeCall {
            native.writeNumber(sheetIndex(sheet), row.toNativeRow(), column.toNativeColumn(), value)
        }
    }

    fun writeBoolean(
        sheet: Worksheet,
        row: Int,
        column: Int,
        value: Boolean,
    ) {
        nativeCall {
            native.writeBoolean(sheetIndex(sheet), row.toNativeRow(), column.toNativeColumn(), value)
        }
    }

    fun writeDate(
        sheet: Worksheet,
        row: Int,
        column: Int,
        value: ExcelDate,
    ) {
        nativeCall {
            native.writeDate(sheetIndex(sheet), row.toNativeRow(), column.toNativeColumn(), value.toNative())
        }
    }

    fun writeDateTime(
        sheet: Worksheet,
        row: Int,
        column: Int,
        value: ExcelDateTime,
    ) {
        nativeCall {
            native.writeDatetime(sheetIndex(sheet), row.toNativeRow(), column.toNativeColumn(), value.toNative())
        }
    }

    fun writeDate(
        sheet: Worksheet,
        row: Int,
        column: Int,
        value: ExcelDate,
        format: String,
    ) {
        nativeCall {
            native.writeDateWithFormat(
                sheetIndex(sheet),
                row.toNativeRow(),
                column.toNativeColumn(),
                value.toNative(),
                format,
            )
        }
    }

    fun setColumnWidth(
        sheet: Worksheet,
        column: Int,
        width: Double,
    ) {
        nativeCall {
            native.setColumnWidth(sheetIndex(sheet), column.toNativeColumn(), width)
        }
    }

    fun setRowHeight(
        sheet: Worksheet,
        row: Int,
        height: Double,
    ) {
        nativeCall {
            native.setRowHeight(sheetIndex(sheet), row.toNativeRow(), height)
        }
    }

    val worksheetCount: Int
        get() = native.worksheetCount().toInt()

    fun save(file: File) {
        nativeCall { native.save(file.absolutePath) }
    }

    fun saveToByteArray(): ByteArray = nativeCall { native.saveToBuffer() }

    override fun close() {
        native.close()
    }

    private fun sheetIndex(sheet: Worksheet): UInt {
        require(sheet.owner === owner) { "worksheet belongs to a different workbook" }
        return sheet.nativeIndex
    }
}

private fun Int.toNativeRow(): UInt {
    require(this in 0..MAX_ROW_INDEX) { "row must be between 0 and $MAX_ROW_INDEX" }
    return toUInt()
}

private fun Int.toNativeColumn(): UShort {
    require(this in 0..MAX_COLUMN_INDEX) { "column must be between 0 and $MAX_COLUMN_INDEX" }
    return toUShort()
}

private inline fun <T> nativeCall(block: () -> T): T =
    try {
        block()
    } catch (error: NativeXlsxWriterException) {
        throw error.toPublicError()
    }

private fun NativeXlsxWriterException.toPublicError(): XlsxWriterException =
    when (this) {
        is NativeXlsxWriterException.IoException -> XlsxWriterException.Io(message.orEmpty(), this)
        is NativeXlsxWriterException.RowColumnLimitException -> XlsxWriterException.RowColumnLimit(message.orEmpty(), this)
        is NativeXlsxWriterException.MaxStringLengthExceeded -> XlsxWriterException.MaxStringLength(message.orEmpty(), this)
        is NativeXlsxWriterException.SheetnameReused -> XlsxWriterException.WorksheetNameReused(message.orEmpty(), this)
        is NativeXlsxWriterException.ParameterException -> XlsxWriterException.InvalidParameter(message.orEmpty(), this)
        is NativeXlsxWriterException.WorksheetNotFound -> XlsxWriterException.WorksheetNotFound(message.orEmpty(), this)
        is NativeXlsxWriterException.DateException -> XlsxWriterException.InvalidDate(message.orEmpty(), this)
        is NativeXlsxWriterException.Unknown -> XlsxWriterException.Unknown(message.orEmpty(), this)
    }
