package com.kharlos.finanzas.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kharlos.finanzas.domain.Cat
import com.kharlos.finanzas.domain.Category
import com.kharlos.finanzas.domain.Goal
import com.kharlos.finanzas.domain.Rule
import com.kharlos.finanzas.domain.Tx

class Db(context: Context) : SQLiteOpenHelper(context, "finanzas.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tx(
            id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, amount INTEGER NOT NULL,
            income INTEGER NOT NULL, bank TEXT NOT NULL, kind TEXT NOT NULL, description TEXT NOT NULL,
            product TEXT, category INTEGER NOT NULL, source TEXT NOT NULL, note TEXT NOT NULL DEFAULT '',
            excluded INTEGER NOT NULL DEFAULT 0, reviewed INTEGER NOT NULL DEFAULT 1, hash TEXT UNIQUE)""")
        db.execSQL("CREATE INDEX tx_time ON tx(time)")
        db.execSQL("""CREATE TABLE category(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, emoji TEXT NOT NULL,
            income INTEGER NOT NULL, exclude_stats INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("CREATE TABLE rule(id INTEGER PRIMARY KEY AUTOINCREMENT, pattern TEXT NOT NULL, category INTEGER NOT NULL, builtin INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE budget(category INTEGER PRIMARY KEY, amount INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE goal(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, emoji TEXT NOT NULL,
            target INTEGER NOT NULL, saved INTEGER NOT NULL, deadline INTEGER)""")
        db.execSQL("CREATE TABLE alert_log(category INTEGER NOT NULL, month TEXT NOT NULL, level INTEGER NOT NULL, PRIMARY KEY(category, month, level))")

        Cat.defaults.forEach { db.insert("category", null, it.toValues(withId = true)) }
        // Los IDs de categorías nuevas arrancan en 100 para no chocar con las fijas
        db.execSQL("UPDATE sqlite_sequence SET seq=99 WHERE name='category'")
        Cat.defaultRules.forEach { (p, c) ->
            db.insert("rule", null, ContentValues().apply { put("pattern", p); put("category", c); put("builtin", 1) })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- Movimientos ----------
    fun insertTx(t: Tx): Long = writableDatabase.insertWithOnConflict("tx", null, t.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
    fun updateTx(t: Tx) { writableDatabase.update("tx", t.toValues(), "id=?", arrayOf(t.id.toString())) }
    fun deleteTx(id: Long) { writableDatabase.delete("tx", "id=?", arrayOf(id.toString())) }
    fun txById(id: Long): Tx? = readableDatabase.rawQuery("SELECT * FROM tx WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toTx() else null }
    fun hashExists(hash: String) = readableDatabase.rawQuery("SELECT 1 FROM tx WHERE hash=?", arrayOf(hash)).use { it.moveToFirst() }

    fun txBetween(from: Long, to: Long): List<Tx> =
        readableDatabase.rawQuery("SELECT * FROM tx WHERE time>=? AND time<? ORDER BY time DESC", arrayOf(from.toString(), to.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.toTx()) } }

    fun allTx(): List<Tx> = txBetween(0, Long.MAX_VALUE)

    /** Compra original que corresponde a un SMS de reverso */
    fun findForReversal(bank: String, amount: Long, description: String, product: String?): Tx? =
        readableDatabase.rawQuery(
            "SELECT * FROM tx WHERE bank=? AND amount=? AND description=? AND IFNULL(product,'')=? AND income=0 AND excluded=0 ORDER BY time DESC LIMIT 1",
            arrayOf(bank, amount.toString(), description, product ?: "")
        ).use { c -> if (c.moveToFirst()) c.toTx() else null }

    fun spentInCategory(category: Long, from: Long, to: Long): Long {
        val where = if (category == Cat.TOTAL) "" else "AND t.category=$category"
        return readableDatabase.rawQuery(
            """SELECT IFNULL(SUM(t.amount),0) FROM tx t JOIN category c ON c.id=t.category
               WHERE t.income=0 AND t.excluded=0 AND c.exclude_stats=0 AND t.time>=? AND t.time<? $where""",
            arrayOf(from.toString(), to.toString())
        ).use { c -> c.moveToFirst(); c.getLong(0) }
    }

    /** Recategoriza movimientos pasados que coinciden con una regla nueva */
    fun applyRule(matches: (String) -> Boolean, category: Long, income: Boolean): Int {
        val ids = readableDatabase.rawQuery("SELECT id, description FROM tx WHERE income=?", arrayOf(if (income) "1" else "0")).use { c ->
            buildList { while (c.moveToNext()) if (matches(c.getString(1))) add(c.getLong(0)) }
        }
        ids.forEach { writableDatabase.update("tx", ContentValues().apply { put("category", category) }, "id=?", arrayOf(it.toString())) }
        return ids.size
    }

    // ---------- Categorías y reglas ----------
    fun categories(): List<Category> = readableDatabase.rawQuery("SELECT * FROM category ORDER BY income, name", null).use { c ->
        buildList { while (c.moveToNext()) add(Category(c.long("id"), c.str("name"), c.str("emoji"), c.long("income") == 1L, c.long("exclude_stats") == 1L)) }
    }
    fun saveCategory(cat: Category) {
        if (cat.id == 0L) writableDatabase.insert("category", null, cat.toValues(withId = false))
        else writableDatabase.update("category", cat.toValues(withId = false), "id=?", arrayOf(cat.id.toString()))
    }

    fun rules(): List<Rule> = readableDatabase.rawQuery("SELECT * FROM rule ORDER BY builtin, pattern", null).use { c ->
        buildList { while (c.moveToNext()) add(Rule(c.long("id"), c.str("pattern"), c.long("category"), c.long("builtin") == 1L)) }
    }
    fun addRule(pattern: String, category: Long) {
        writableDatabase.insert("rule", null, ContentValues().apply { put("pattern", pattern.trim()); put("category", category); put("builtin", 0) })
    }
    fun deleteRule(id: Long) { writableDatabase.delete("rule", "id=?", arrayOf(id.toString())) }

    // ---------- Presupuestos y alertas ----------
    fun budgets(): Map<Long, Long> = readableDatabase.rawQuery("SELECT category, amount FROM budget", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), c.getLong(1)) }
    }
    fun setBudget(category: Long, cents: Long?) {
        if (cents == null || cents <= 0) writableDatabase.delete("budget", "category=?", arrayOf(category.toString()))
        else writableDatabase.insertWithOnConflict("budget", null, ContentValues().apply { put("category", category); put("amount", cents) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    /** true si la alerta es nueva (no se había mandado este mes) */
    fun markAlert(category: Long, month: String, level: Int): Boolean =
        writableDatabase.insertWithOnConflict("alert_log", null, ContentValues().apply {
            put("category", category); put("month", month); put("level", level)
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L

    // ---------- Metas ----------
    fun goals(): List<Goal> = readableDatabase.rawQuery("SELECT * FROM goal ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(Goal(c.long("id"), c.str("name"), c.str("emoji"), c.long("target"), c.long("saved"),
            c.getColumnIndexOrThrow("deadline").let { if (c.isNull(it)) null else c.getLong(it) })) }
    }
    fun saveGoal(g: Goal) {
        val v = ContentValues().apply {
            put("name", g.name); put("emoji", g.emoji); put("target", g.targetCents); put("saved", g.savedCents)
            if (g.deadlineMillis != null) put("deadline", g.deadlineMillis) else putNull("deadline")
        }
        if (g.id == 0L) writableDatabase.insert("goal", null, v) else writableDatabase.update("goal", v, "id=?", arrayOf(g.id.toString()))
    }
    fun deleteGoal(id: Long) { writableDatabase.delete("goal", "id=?", arrayOf(id.toString())) }

    // ---------- Mapeos ----------
    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col))
    private fun Cursor.strOrNull(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }

    private fun Cursor.toTx() = Tx(
        id = long("id"), timeMillis = long("time"), amountCents = long("amount"), isIncome = long("income") == 1L,
        bank = str("bank"), kind = str("kind"), description = str("description"), product = strOrNull("product"),
        categoryId = long("category"), source = str("source"), note = str("note"),
        excluded = long("excluded") == 1L, reviewed = long("reviewed") == 1L, hash = strOrNull("hash"),
    )

    private fun Tx.toValues() = ContentValues().apply {
        put("time", timeMillis); put("amount", amountCents); put("income", if (isIncome) 1 else 0)
        put("bank", bank); put("kind", kind); put("description", description); put("product", product)
        put("category", categoryId); put("source", source); put("note", note)
        put("excluded", if (excluded) 1 else 0); put("reviewed", if (reviewed) 1 else 0); put("hash", hash)
    }

    private fun Category.toValues(withId: Boolean) = ContentValues().apply {
        if (withId) put("id", id)
        put("name", name); put("emoji", emoji); put("income", if (income) 1 else 0); put("exclude_stats", if (excludeFromStats) 1 else 0)
    }
}
