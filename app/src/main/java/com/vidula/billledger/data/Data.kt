package com.vidula.billledger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

val CATEGORIES = listOf(
    "Housing", "Utilities", "Telecom", "Groceries", "Dining", "Transport", "Shopping",
    "Health", "Subscriptions", "Education", "Insurance", "Other"
)
val UNITS = listOf("pcs", "L", "kg", "service")

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey val id: String,
    val date: String,          // yyyy-MM-dd
    val vendor: String,
    val category: String,
    val total: Double,
    val currency: String,
    val due: String = "",      // yyyy-MM-dd or ""
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val month: String get() = date.take(7)
    val year: String get() = date.take(4)
}

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("billId"), Index("item")]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: String = "",
    val item: String,          // generic name, e.g. "Milk"
    val name: String = "",     // as printed, e.g. "Almarai Full Fat Milk 2L"
    val qty: Double = 1.0,
    val unit: String = "pcs",
    val amount: Double = 0.0,
)

data class BillWithItems(
    @Embedded val bill: Bill,
    @Relation(parentColumn = "id", entityColumn = "billId") val items: List<Item>,
)

@Dao
abstract class BillDao {
    @Transaction
    @Query("SELECT * FROM bills ORDER BY date DESC, createdAt DESC")
    abstract fun observeAll(): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills ORDER BY date DESC, createdAt DESC")
    abstract suspend fun all(): List<BillWithItems>

    @Upsert
    abstract suspend fun upsertBill(bill: Bill)

    @Insert
    abstract suspend fun insertItems(items: List<Item>)

    @Query("DELETE FROM items WHERE billId = :billId")
    abstract suspend fun deleteItems(billId: String)

    @Query("DELETE FROM bills WHERE id = :billId")
    abstract suspend fun deleteBill(billId: String)

    @Transaction
    open suspend fun save(bill: Bill, items: List<Item>) {
        upsertBill(bill)
        deleteItems(bill.id)
        if (items.isNotEmpty()) insertItems(items.map { it.copy(id = 0, billId = bill.id) })
    }
}

@Database(entities = [Bill::class, Item::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun bills(): BillDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "bill-ledger.db")
                .build().also { instance = it }
        }
    }
}

fun newBillId(): String =
    "b" + System.currentTimeMillis().toString(36) + (0 until 5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

/** Turns "  almarai  MILK " into "Almarai Milk". */
fun titleCase(s: String): String =
    s.trim().replace(Regex("\\s+"), " ").lowercase()
        .split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.take(50)
