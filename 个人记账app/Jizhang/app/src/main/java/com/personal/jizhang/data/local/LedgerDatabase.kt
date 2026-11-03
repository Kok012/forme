package com.personal.jizhang.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    // Room invalidates this query on ANY change to either table. Do not apply distinctUntilChanged:
    // an edit often leaves both row counts unchanged, but must still refresh the ledger.
    @Query("SELECT (SELECT COUNT(*) FROM categories) + (SELECT COUNT(*) FROM expense_records)")
    fun observeChanges(): Flow<Long>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun categories(): List<Category>

    @Query("SELECT * FROM expense_records ORDER BY expenseDate DESC, createdAt DESC, id DESC")
    suspend fun records(): List<Expense>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: String): Category?

    @Query("SELECT * FROM expense_records WHERE id = :id")
    suspend fun record(id: String): Expense?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Query("SELECT COUNT(*) FROM expense_records WHERE categoryId = :id")
    suspend fun referencesTo(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecords(records: List<Expense>)

    @Upsert suspend fun saveCategory(category: Category)
    @Upsert suspend fun saveRecord(record: Expense)
    @Query("DELETE FROM expense_records WHERE id = :id") suspend fun deleteRecord(id: String)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)
    @Query("DELETE FROM expense_records") suspend fun deleteAllRecords()
    @Query("DELETE FROM categories") suspend fun deleteAllCategories()
}

@Database(entities = [Category::class, Expense::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(
            context.applicationContext, LedgerDatabase::class.java, "jizhang.db",
        ).build()
    }
}
