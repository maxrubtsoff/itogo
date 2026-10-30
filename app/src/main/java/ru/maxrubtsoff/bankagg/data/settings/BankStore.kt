package ru.maxrubtsoff.bankagg.data.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.maxrubtsoff.bankagg.data.local.AppDatabase
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankDirectory
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository

/**
 * The user's banks in small preferences (F-22): the ordered keys, plus the full
 * description of every bank that isn't a known one (CBR and own banks), so they
 * resolve anywhere through [BankDirectory]. An install from before F-22 gets its
 * banks from the tracked packages and the data it already has.
 */
class BankStore(
    context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository
) : BankRepository {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("banks", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _mine = MutableStateFlow(readStored() ?: emptyList())
    override val myBanks: Flow<List<BankBrand>> = _mine.asStateFlow()

    private var cbr: List<CbrBanks.Entry>? = null

    init {
        BankDirectory.replace(readDefinitions(), activeKeys = _mine.value.map { it.key }.toSet())
    }

    /** Fills the list once for an install that had banks before F-22. */
    suspend fun migrate() = lock.withLock {
        if (prefs.contains(KEY_MINE)) return@withLock
        val keys = withContext(Dispatchers.IO) {
            val fromPackages = settings.currentSelectedPackages().map { BankBrand.fromPackage(it).key }
            val fromData = runCatching {
                db.openHelper.readableDatabase.query(
                    "SELECT brandKey FROM cashback UNION SELECT brandKey FROM deposits UNION SELECT brandKey FROM accounts"
                ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            }.getOrDefault(emptyList())
            (fromPackages + fromData).filter { it.isNotBlank() && it != BankBrand.UNKNOWN.key }.distinct()
        }
        val banks = keys.map { BankBrand.fromKey(it) }.sortedBy { it.order }
        write(banks)
    }

    override suspend fun current(): List<BankBrand> = _mine.value

    override suspend fun add(bank: BankBrand) = lock.withLock {
        if (_mine.value.any { it.key == bank.key }) return@withLock
        write(_mine.value + bank)
    }

    override suspend fun update(bank: BankBrand) = lock.withLock {
        write(_mine.value.map { if (it.key == bank.key) bank else it })
    }

    override suspend fun remove(key: String) = lock.withLock {
        write(_mine.value.filterNot { it.key == key })
    }

    /** A restored backup's banks replace the current list. */
    suspend fun replaceAll(banks: List<BankBrand>) = lock.withLock { write(banks) }

    override suspend fun cbrEntries(): List<CbrBanks.Entry> = cbr ?: withContext(Dispatchers.IO) {
        CbrBanks.parse(appContext.assets.open("banks/cbr.tsv").bufferedReader().use { it.readText() })
    }.also { cbr = it }

    private fun write(banks: List<BankBrand>) {
        // Everything the user may have changed is stored, a known bank included:
        // its picture and letters would otherwise be lost on the next read (B-49).
        // BankBrand.equals is by key only, so compare the look itself.
        val changed = banks.filter { b ->
            val k = BankBrand.known.firstOrNull { it.key == b.key }
            k == null || b.logoFile != null || b.letters != k.letters ||
                b.primaryColor != k.primaryColor || b.textColor != k.textColor || b.title != k.title ||
                b.packages != k.packages
        }
        val defs = (readDefinitions().associateBy { it.key } + changed.associateBy { it.key }).values
        prefs.edit()
            .putString(KEY_MINE, JSONArray(banks.map { it.key }).toString())
            .putString(KEY_DEFS, JSONArray(defs.map(::toJson)).toString())
            .apply()
        BankDirectory.replace(defs.toList(), activeKeys = banks.map { it.key }.toSet())
        _mine.value = banks
    }

    private fun readStored(): List<BankBrand>? {
        val raw = prefs.getString(KEY_MINE, null) ?: return null
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { BankBrand.fromKey(arr.getString(it)) }
    }

    private fun readDefinitions(): List<BankBrand> {
        val raw = prefs.getString(KEY_DEFS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }
    }

    companion object {
        private const val KEY_MINE = "mine"
        private const val KEY_DEFS = "defs"

        fun toJson(b: BankBrand): JSONObject = JSONObject()
            .put("key", b.key)
            .put("title", b.title)
            .put("color", b.primaryColor)
            .put("letters", b.letters)
            .put("packages", JSONArray(b.packages.toList()))
            .apply {
                b.textColor?.let { put("textColor", it) }
                b.logoFile?.let { put("logo", it) }
                b.regNumber?.let { put("reg", it) }
                put("currency", b.defaultCurrency ?: JSONObject.NULL)
            }

        fun fromJson(o: JSONObject): BankBrand {
            val pk = o.optJSONArray("packages")
            return BankBrand(
                key = o.getString("key"),
                title = o.getString("title"),
                primaryColor = o.getLong("color"),
                packages = (0 until (pk?.length() ?: 0)).map { pk!!.getString(it) }.toSet(),
                defaultCurrency = if (o.isNull("currency")) null else o.optString("currency", "RUB"),
                letters = o.optString("letters").ifBlank { BankBrand.lettersOf(o.getString("title")) },
                textColor = if (o.has("textColor")) o.getLong("textColor") else null,
                logoFile = o.optString("logo").ifBlank { null },
                regNumber = if (o.has("reg")) o.getInt("reg") else null
            )
        }
    }
}
