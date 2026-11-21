package com.vidula.billledger.excel

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Cell styles defined in styles.xml below. */
enum class Style(val xf: Int) { PLAIN(0), DATE(1), MONEY(2), HEADER(3), NUMBER(4) }

sealed class Cell {
    data class Text(val s: String, val style: Style = Style.PLAIN) : Cell()
    data class Num(val v: Double, val style: Style = Style.NUMBER) : Cell()
    /** A formula with the value Excel would compute, so the file reads right before Excel recalculates. */
    data class Formula(val f: String, val cached: Double, val style: Style = Style.NUMBER) : Cell()
    data object Empty : Cell()
}

class Sheet(val name: String, val header: List<String>, val widths: List<Int>, val rows: List<List<Cell>>)

/** Writes a minimal, valid .xlsx (Office Open XML) with inline strings, number formats and formulas. */
object XlsxWriter {
    fun write(sheets: List<Sheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(path: String, xml: String) { z.putNextEntry(ZipEntry(path)); z.write(xml.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets>")
                // autofilter ranges must be named per sheet for Excel to keep them
                append("<definedNames>")
                sheets.forEachIndexed { i, s -> append("""<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(s.name.replace("'", "''"))}'!${'$'}A${'$'}1:${'$'}${col(s.header.size - 1)}${'$'}${s.rows.size + 1}</definedName>""") }
                append("</definedNames>")
                append("""<calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            })
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy-mm-dd"/><numFmt numFmtId="165" formatCode="#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD5DBE0"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return out.toByteArray()
    }

    private fun sheetXml(s: Sheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        s.widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        val all = listOf(s.header.map { Cell.Text(it, Style.HEADER) }) + s.rows
        all.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = col(c) + (r + 1)
                when (cell) {
                    is Cell.Text -> if (cell.s.isNotEmpty()) append("""<c r="$ref" t="inlineStr" s="${cell.style.xf}"><is><t xml:space="preserve">${esc(cell.s)}</t></is></c>""")
                    is Cell.Num -> append("""<c r="$ref" s="${cell.style.xf}"><v>${num(cell.v)}</v></c>""")
                    is Cell.Formula -> append("""<c r="$ref" s="${cell.style.xf}"><f>${esc(cell.f)}</f><v>${num(cell.cached)}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        append("""<autoFilter ref="A1:${col(s.header.size - 1)}${all.size}"/>""")
        append("</worksheet>")
    }

    private fun num(v: Double) = if (v.isNaN() || v.isInfinite()) "0" else if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    fun col(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }; return sb.toString() }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }
}

/** Reads every sheet of an .xlsx into rows of strings keyed by the header row. Handles files re-saved by Excel. */
object XlsxReader {
    fun read(input: InputStream): Map<String, List<Map<String, String>>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name.trimStart('/')
                if (n.endsWith(".xml") || n.endsWith(".rels")) files[n] = z.readBytes()
                e = z.nextEntry
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) }.orEmpty()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { relationships(it) }.orEmpty()
        val wb = files["xl/workbook.xml"] ?: error("Not an Excel workbook")
        val out = linkedMapOf<String, List<Map<String, String>>>()
        for ((name, rid) in sheetList(wb)) {
            val target = rels[rid] ?: continue
            val path = if (target.startsWith("/")) target.trimStart('/') else "xl/" + target.removePrefix("./")
            val data = files[path] ?: continue
            val grid = cells(data, shared)
            if (grid.isEmpty()) continue
            val header = grid.first()
            out[name] = grid.drop(1).map { row -> header.mapIndexedNotNull { i, h -> if (h.isBlank()) null else h.trim() to row.getOrElse(i) { "" } }.toMap() }
                .filter { r -> r.values.any { it.isNotBlank() } }
        }
        return out
    }

    private fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(bytes.inputStream(), "UTF-8")
    }

    private fun local(n: String) = n.substringAfter(':')

    private fun sharedStrings(b: ByteArray): List<String> {
        val p = parser(b); val out = mutableListOf<String>(); var cur: StringBuilder? = null; var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
            XmlPullParser.START_TAG -> when (local(p.name)) { "si" -> cur = StringBuilder(); "t" -> inT = true; "rPh" -> inT = false }
            XmlPullParser.TEXT -> if (inT) cur?.append(p.text)
            XmlPullParser.END_TAG -> when (local(p.name)) { "t" -> inT = false; "si" -> { out += cur?.toString() ?: ""; cur = null } }
        }
        return out
    }

    private fun relationships(b: ByteArray): Map<String, String> {
        val p = parser(b); val out = mutableMapOf<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && local(p.name) == "Relationship")
            out[p.getAttributeValue(null, "Id")] = p.getAttributeValue(null, "Target")
        return out
    }

    private fun sheetList(b: ByteArray): List<Pair<String, String>> {
        val p = parser(b); val out = mutableListOf<Pair<String, String>>()
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && local(p.name) == "sheet") {
            val rid = (0 until p.attributeCount).firstOrNull { local(p.getAttributeName(it)) == "id" }?.let { p.getAttributeValue(it) } ?: continue
            out += p.getAttributeValue(null, "name") to rid
        }
        return out
    }

    private fun colIndex(ref: String): Int { var n = 0; for (ch in ref) { if (!ch.isLetter()) break; n = n * 26 + (ch.uppercaseChar() - 'A' + 1) }; return n - 1 }

    private fun cells(b: ByteArray, shared: List<String>): List<List<String>> {
        val p = parser(b); val rows = mutableListOf<MutableList<String>>()
        var row: MutableList<String>? = null; var col = 0; var type = ""; var text = StringBuilder(); var inV = false
        while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
            XmlPullParser.START_TAG -> when (local(p.name)) {
                "row" -> row = mutableListOf()
                "c" -> { col = p.getAttributeValue(null, "r")?.let { colIndex(it) } ?: (row?.size ?: 0); type = p.getAttributeValue(null, "t") ?: ""; text = StringBuilder() }
                "v", "t" -> inV = true
            }
            XmlPullParser.TEXT -> if (inV) text.append(p.text)
            XmlPullParser.END_TAG -> when (local(p.name)) {
                "v", "t" -> inV = false
                "c" -> row?.let { r ->
                    while (r.size <= col) r += ""
                    val raw = text.toString()
                    r[col] = when (type) { "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""; "b" -> if (raw == "1") "TRUE" else "FALSE"; else -> raw }
                }
                "row" -> { row?.let(rows::add); row = null }
            }
        }
        return rows
    }
}
