package com.vidula.billledger.reading

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vidula.billledger.data.Item
import com.vidula.billledger.data.titleCase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.Month

/**
 * Free, offline reading: Google ML Kit finds the text, then simple rules pick out
 * the payee, dates, total and item lines. Every result can be edited.
 */
object OnDeviceReader {

    suspend fun read(pages: List<Bitmap>, defaultCurrency: String): Draft {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val rows = mutableListOf<String>()
        try {
            for (page in pages) rows += toRows(recognizer.process(InputImage.fromBitmap(page, 0)).await())
        } finally {
            recognizer.close()
        }
        return parse(rows, defaultCurrency)
    }

    /** ML Kit returns columns as separate blocks; rebuild printed rows by grouping lines at the same height. */
    private fun toRows(text: com.google.mlkit.vision.text.Text): List<String> {
        data class L(val t: String, val x: Int, val cy: Int, val h: Int)
        val lines = text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
            val r = l.boundingBox ?: return@mapNotNull null
            L(l.text, r.left, r.centerY(), r.height())
        }.sortedBy { it.cy }
        if (lines.isEmpty()) return emptyList()
        val tol = (lines.map { it.h }.sorted()[lines.size / 2] * 0.6).coerceAtLeast(6.0)
        val rows = mutableListOf<MutableList<L>>()
        for (l in lines) {
            val last = rows.lastOrNull()
            if (last != null && kotlin.math.abs(last.map { it.cy }.average() - l.cy) <= tol) last += l else rows += mutableListOf(l)
        }
        return rows.map { r -> r.sortedBy { it.x }.joinToString("   ") { it.t } }
    }

    // ---------- rules ----------

    private val PRICE = Regex("""(-?\d{1,3}(?:,\d{3})*\.\d{2}|-?\d+\.\d{2})(?!\d)""")
    private val TRAILING_PRICE = Regex("""^(.*?)[\s:]*(?:AED|USD|EUR|GBP|INR|SAR|DHS|Dhs|Rs\.?|[${'$'}€£₹])?\s*(-?\d[\d,]*\.\d{2})\s*(?:AED|Dhs|[A-Z])?\s*$""")
    private val NOT_ITEM = Regex("""(?i)\b(sub\s*-?\s*total|total|vat|tax|gst|change|cash|card|visa|master|mada|discount|saving|round|balance|paid|tender|points|tel|phone|trn|invoice|receipt|amount|net|due|payment|qty|item\s*count|thank)\b""")
    private val GRAND = Regex("""(?i)\b(grand\s*total|total\s*(amount\s*)?(due|payable)|amount\s*(due|payable)|net\s*(amount|payable|total)|balance\s*due|total\s*incl|total\s*amount|to\s*pay)\b""")
    private val TOTAL = Regex("""(?i)\btotal\b""")
    private val SUBTOTAL = Regex("""(?i)\b(sub\s*-?\s*total|total\s*(vat|tax|items?|qty|discount|savings?)|vat|tax)\b""")

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    private val D_ISO = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""")
    private val D_DMY = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2}|\d{2})\b""")
    private val D_NAMED = Regex("""(?i)\b(\d{1,2})[\s-]*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\s,-]*(20\d{2})\b""")
    private val D_NAMED2 = Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+(\d{1,2}),?\s+(20\d{2})\b""")

    private val SIZE = Regex("""(?i)(\d+(?:\.\d+)?)\s*(ml|ltr|litre|liter|lt|l|kg|gm|grams?|g)\b""")
    private val COUNT = Regex("""(?i)(?:^|\s)(\d{1,3}(?:\.\d+)?)\s*[x×@*]\s*""")
    private val PACK = Regex("""(?i)\b(\d{1,3})\s*(?:s|pcs|pc|pack|pk)\b""")

    /** Common products, so "ALMARAI FF MILK 2L" is tracked as Milk. */
    private val PRODUCTS = linkedMapOf(
        "laban" to "Laban", "milk" to "Milk", "egg" to "Eggs", "bread" to "Bread", "rice" to "Rice", "chicken" to "Chicken",
        "water" to "Water", "banana" to "Bananas", "apple" to "Apples", "tomato" to "Tomatoes", "onion" to "Onions",
        "potato" to "Potatoes", "cheese" to "Cheese", "yogh" to "Yogurt", "yogu" to "Yogurt", "labneh" to "Labneh",
        "butter" to "Butter", "juice" to "Juice", "sugar" to "Sugar", "oil" to "Cooking Oil", "flour" to "Flour",
        "atta" to "Flour", "tea" to "Tea", "coffee" to "Coffee", "beef" to "Beef", "mutton" to "Mutton", "lamb" to "Lamb",
        "fish" to "Fish", "salmon" to "Fish", "pasta" to "Pasta", "cereal" to "Cereal", "biscuit" to "Biscuits",
        "chocolate" to "Chocolate", "tissue" to "Tissues", "detergent" to "Detergent", "shampoo" to "Shampoo",
        "soap" to "Soap", "diaper" to "Diapers", "cucumber" to "Cucumbers", "carrot" to "Carrots", "lettuce" to "Lettuce",
        "orange" to "Oranges", "grape" to "Grapes", "mango" to "Mangoes", "dates" to "Dates", "petrol" to "Fuel",
        "diesel" to "Fuel", "super 98" to "Fuel", "special 95" to "Fuel", "e-plus 91" to "Fuel",
    )

    private val VENDOR_CATS = listOf(
        "dewa|sewa|fewa|addc|aadc|electricity|water authority|empower|tabreed" to "Utilities",
        "\\bdu\\b|etisalat|\\be&\\b|virgin mobile|internet|broadband|telecom|airtel|jio|vodafone" to "Telecom",
        "carrefour|lulu|spinneys|waitrose|choithrams|union coop|coop|kibsons|grandiose|nesto|safeer|viva|al maya|supermarket|hypermarket|grocery|baqala|bakala" to "Groceries",
        "talabat|deliveroo|careem food|noon food|restaurant|cafe|café|coffee|starbucks|mcdonald|kfc|pizza|burger|shawarma|zomato|swiggy" to "Dining",
        "salik|enoc|adnoc|eppco|emarat|rta|nol|uber|careem|taxi|parking|petrol|fuel" to "Transport",
        "noon|amazon|ikea|centrepoint|max fashion|h&m|zara|sharaf dg|jumbo|ace hardware|daiso" to "Shopping",
        "pharmacy|clinic|hospital|aster|medcare|mediclinic|nmc|life pharmacy|boots|dental|lab" to "Health",
        "netflix|spotify|shahid|osn|disney|youtube|apple\\.com|icloud|google one|anghami|prime" to "Subscriptions",
        "school|university|tuition|course|academy|books" to "Education",
        "insurance|takaful|axa|orient|daman|oman insurance" to "Insurance",
        "rent|ejari|real estate|properties|service charge|maintenance|dubizzle|bayut|tenancy" to "Housing",
    ).map { (k, v) -> Regex("(?i)($k)") to v }

    private val SERVICE_ITEMS = mapOf(
        "Utilities" to "Electricity & Water", "Telecom" to "Phone & Internet", "Housing" to "Rent",
        "Subscriptions" to "Subscription", "Insurance" to "Insurance", "Education" to "Fees"
    )

    fun parse(rows: List<String>, defaultCurrency: String): Draft {
        val clean = rows.map { it.trim() }.filter { it.isNotEmpty() }
        val prices = clean.flatMap { r -> PRICE.findAll(r).map { it.value.replace(",", "").toDouble() } }
        if (clean.isEmpty() || prices.isEmpty()) throw NotABillException()

        val vendor = clean.take(6).firstOrNull { r ->
            r.count { it.isLetter() } >= 3 && !Regex("(?i)tax invoice|^invoice|^receipt|simplified|welcome|trn|tel|phone|www\\.|date|bill no|^cashier").containsMatchIn(r)
        }?.let { titleCase(it.replace(Regex("[^\\p{L}\\p{N}&' .-]"), " ")) } ?: "Unknown"

        val dueRow = clean.firstOrNull { Regex("(?i)\\b(due|pay by|payment date)\\b").containsMatchIn(it) && findDate(it) != null }
        val due = dueRow?.let { findDate(it) } ?: ""
        val date = clean.filter { it != dueRow }.firstNotNullOfOrNull { findDate(it) } ?: due.ifBlank { LocalDate.now().toString() }

        fun lastPrice(r: String) = PRICE.findAll(r).lastOrNull()?.value?.replace(",", "")?.toDouble()
        val total = clean.lastOrNull { GRAND.containsMatchIn(it) && lastPrice(it) != null }?.let { lastPrice(it) }
            ?: clean.lastOrNull { TOTAL.containsMatchIn(it) && !SUBTOTAL.containsMatchIn(it) && lastPrice(it) != null }?.let { lastPrice(it) }
            ?: prices.max()

        val all = clean.joinToString(" ")
        val currency = when {
            Regex("(?i)\\b(aed|dhs?|dirham)\\b|د\\.إ").containsMatchIn(all) -> "AED"
            Regex("(?i)\\b(sar|riyal)\\b").containsMatchIn(all) -> "SAR"
            Regex("(?i)\\binr\\b|₹|\\brs\\.?\\s").containsMatchIn(all) -> "INR"
            all.contains("€") || Regex("(?i)\\beur\\b").containsMatchIn(all) -> "EUR"
            all.contains("£") || Regex("(?i)\\bgbp\\b").containsMatchIn(all) -> "GBP"
            all.contains("$") || Regex("(?i)\\busd\\b").containsMatchIn(all) -> "USD"
            else -> defaultCurrency
        }

        val items = clean.mapNotNull { r ->
            if (NOT_ITEM.containsMatchIn(r)) return@mapNotNull null
            val m = TRAILING_PRICE.find(r) ?: return@mapNotNull null
            val label = m.groupValues[1].trim()
            val amount = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
            // drop a unit-price column such as "2 x 6.75" left in the label
            val name = label.replace(PRICE, " ").replace(Regex("\\s{2,}"), " ").trim()
            if (name.count { it.isLetter() } < 2 || amount <= 0 || amount > total + 0.01) return@mapNotNull null
            val (qty, unit) = quantity(name)
            Item(item = generic(name), name = name, qty = qty, unit = unit, amount = amount)
        }

        val category = VENDOR_CATS.firstOrNull { (re, _) -> re.containsMatchIn(vendor) || re.containsMatchIn(clean.take(8).joinToString(" ")) }?.second
            ?: if (items.size >= 4) "Groceries" else "Other"

        val finalItems = if (items.isEmpty() && category in SERVICE_ITEMS)
            listOf(Item(item = SERVICE_ITEMS.getValue(category), name = vendor, qty = 1.0, unit = "service", amount = total))
        else items

        return cleanDraft(Draft(vendor, date, due, total, currency, category, finalItems, "Read on device"), defaultCurrency)
    }

    private fun quantity(name: String): Pair<Double, String> {
        val count = COUNT.find(name)?.groupValues?.get(1)?.toDoubleOrNull()
        val size = SIZE.find(name)
        if (size != null) {
            val v = size.groupValues[1].toDouble()
            val (amount, unit) = when (size.groupValues[2].lowercase()) {
                "ml" -> v / 1000 to "L"
                "g", "gm", "gram", "grams" -> v / 1000 to "kg"
                "kg" -> v to "kg"
                else -> v to "L"
            }
            return (amount * (count ?: 1.0)) to unit
        }
        val pack = PACK.find(name)?.groupValues?.get(1)?.toDoubleOrNull()
        return ((pack ?: 1.0) * (count ?: 1.0)) to "pcs"
    }

    private fun generic(name: String): String {
        val low = name.lowercase()
        // match at the start of a word, so "steak" isn't tea and "toilet" isn't oil
        PRODUCTS.entries.firstOrNull { Regex("\\b" + Regex.escape(it.key)).containsMatchIn(low) }?.let { return it.value }
        val words = name.replace(SIZE, " ").replace(Regex("[^\\p{L} ]"), " ").trim().split(Regex("\\s+")).filter { it.length > 1 }
        return titleCase(words.take(3).joinToString(" ")).ifBlank { titleCase(name) }
    }

    private fun findDate(r: String): String? {
        fun ok(y: Int, m: Int, d: Int) = runCatching { LocalDate.of(y, Month.of(m), d) }.getOrNull()
            ?.takeIf { it.year in 2000..LocalDate.now().year + 1 }?.toString()
        D_ISO.find(r)?.let { m -> ok(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it } }
        D_NAMED.find(r)?.let { m -> ok(m.groupValues[3].toInt(), MONTHS.getValue(m.groupValues[2].lowercase().take(3)), m.groupValues[1].toInt())?.let { return it } }
        D_NAMED2.find(r)?.let { m -> ok(m.groupValues[3].toInt(), MONTHS.getValue(m.groupValues[1].lowercase().take(3)), m.groupValues[2].toInt())?.let { return it } }
        D_DMY.find(r)?.let { m ->
            var y = m.groupValues[3].toInt(); if (y < 100) y += 2000
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            // day-first unless that is impossible
            return ok(y, b, a) ?: ok(y, a, b)
        }
        return null
    }
}
