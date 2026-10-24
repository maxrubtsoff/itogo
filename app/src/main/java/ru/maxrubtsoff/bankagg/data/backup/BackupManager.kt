package ru.maxrubtsoff.bankagg.data.backup

import ru.maxrubtsoff.bankagg.data.local.DepositTopUpEntity
import ru.maxrubtsoff.bankagg.data.settings.BankStore
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import org.json.JSONObject
import ru.maxrubtsoff.bankagg.data.local.AccountEntity
import ru.maxrubtsoff.bankagg.data.local.AppDatabase
import ru.maxrubtsoff.bankagg.data.local.CashbackEntity
import ru.maxrubtsoff.bankagg.data.local.CashbackShotEntity
import ru.maxrubtsoff.bankagg.data.local.DepositEntity
import ru.maxrubtsoff.bankagg.data.local.DepositShotEntity
import ru.maxrubtsoff.bankagg.data.local.EventEntity
import ru.maxrubtsoff.bankagg.data.local.CashEntity
import ru.maxrubtsoff.bankagg.data.local.FxOperationEntity
import ru.maxrubtsoff.bankagg.data.local.FxRateEntity
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.data.local.TransactionEntity
import ru.maxrubtsoff.bankagg.data.local.SubscriptionEntity
import ru.maxrubtsoff.bankagg.domain.repository.CandidateRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
import ru.maxrubtsoff.bankagg.domain.model.BackupRotation
import ru.maxrubtsoff.bankagg.domain.model.CopyKind

/**
 * Serialises all local data to JSON and restores it. Copies are written by the
 * app itself ([AutoBackup]), one file per day, to Downloads/BankAggregator on
 * Android 10+ (otherwise the app's external files dir) — the user never sees a path.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val banks: BankStore? = null,
    private val candidates: CandidateRepository? = null
) {
    private val ledger get() = db.ledgerDao()
    private val events get() = db.eventDao()
    private val cashback get() = db.cashbackDao()
    private val shots get() = db.cashbackShotDao()
    private val deposits get() = db.depositDao()
    private val depositShots get() = db.depositShotDao()
    private val fx get() = db.fxDao()
    private val topUps get() = db.depositTopUpDao()
    private val subscriptions get() = db.subscriptionDao()

    /**
     * One copy in the list (U-30): when it was made and by what. [ref] is how to
     * read it (a content URI or a file path).
     */
    data class Copy(val day: LocalDate, val time: LocalTime, val kind: CopyKind, val ref: String, val name: String)

    /** How opening a picked file went (F-26). */
    sealed interface FileResult {
        data object Restored : FileResult
        /** A password copy: ask for the password and call [restoreFrom] again with it. */
        data object NeedsPassword : FileResult
        data object WrongPassword : FileResult
    }

    // ---- copies kept by the app ----

    /** Writes the current data as [today]'s automatic copy (replacing an earlier one of the day) and drops old copies. */
    suspend fun autoSave(today: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        val key = PhoneKeys.backupKey()
        val bytes = export().toByteArray()
        // Without a working Keystore the copy stays plain rather than not being made at all.
        if (key != null) write(BackupRotation.autoName(today), BackupCipher.seal(bytes, key))
        else write(BackupRotation.plainName(today), bytes)
    }

    /** «Сохранить сейчас»: a copy with its time that the automatic one never rewrites. */
    suspend fun saveNow(at: LocalDateTime = LocalDateTime.now()) = withContext(Dispatchers.IO) {
        writeSealed(BackupRotation.manualName(at))
    }

    /** Copies this install can see, newest first. */
    suspend fun copies(): List<Copy> = withContext(Dispatchers.IO) {
        listAll().mapNotNull { f ->
            val c = BackupRotation.parse(f.name) ?: return@mapNotNull null
            val time = c.time ?: Instant.ofEpochMilli(f.modifiedAt).atZone(ZoneId.systemDefault()).toLocalTime()
            Copy(c.day, time.withSecond(0).withNano(0), c.kind, f.ref, f.name)
        }.sortedWith(compareByDescending<Copy> { it.day }.thenByDescending { it.time })
    }

    /**
     * Replaces the data with [copy]. First the current data is saved as a
     * «до восстановления» copy (B-26), returned so the restore can be undone;
     * [keepCurrent] = false when undoing, which needs no copy of its own.
     */
    suspend fun restore(copy: Copy, keepCurrent: Boolean = true): Copy? = withContext(Dispatchers.IO) {
        val bytes = runCatching { readRef(copy.ref) }.getOrElse { throw if (it is SafeFiles.TooBig) RestoreError.NotOurs() else RestoreError.Corrupt() }
        val json = when (BackupCipher.kind(bytes)) {
            BackupCipher.Kind.PLAIN -> String(bytes, Charsets.UTF_8)
            BackupCipher.Kind.DEVICE -> openWithPhoneKey(bytes)
            else -> throw RestoreError.Corrupt()
        }
        val before = if (keepCurrent) saveBefore() else null
        import(json)
        before
    }

    /**
     * A file the user picked (after a reinstall, from another phone). A password
     * copy needs [password]; a copy sealed with this phone's key opens only here.
     */
    suspend fun restoreFrom(uri: Uri, password: CharArray? = null): FileResult = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { SafeFiles.readLimitedBytes(it) }
        }.getOrElse { if (it is SafeFiles.TooBig) throw RestoreError.NotOurs() else null } ?: throw RestoreError.Corrupt()
        val json = when (BackupCipher.kind(bytes)) {
            BackupCipher.Kind.PLAIN -> String(bytes, Charsets.UTF_8)
            BackupCipher.Kind.DEVICE -> openWithPhoneKey(bytes)
            BackupCipher.Kind.PASSWORD -> {
                password ?: return@withContext FileResult.NeedsPassword
                try {
                    String(BackupCipher.openWithPassword(bytes, password), Charsets.UTF_8)
                } catch (e: BackupCipher.WrongKey) {
                    return@withContext FileResult.WrongPassword
                }
            }
            BackupCipher.Kind.UNKNOWN -> throw RestoreError.NotOurs()
        }
        saveBefore()
        import(json)
        FileResult.Restored
    }

    /**
     * A copy to send elsewhere (U-30, F-26): sealed with the backup password, in
     * the app's cache for the share sheet. Returns the file.
     */
    suspend fun exportForSharing(password: CharArray, at: LocalDateTime = LocalDateTime.now()): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, BackupRotation.manualName(at).replace("-manual", "")).apply {
            writeBytes(BackupCipher.sealWithPassword(export().toByteArray(), password))
        }
    }

    private suspend fun saveBefore(): Copy? = runCatching {
        val at = LocalDateTime.now()
        val name = BackupRotation.beforeName(at)
        val ref = writeSealed(name)
        Copy(at.toLocalDate(), at.toLocalTime().withSecond(0).withNano(0), CopyKind.BEFORE_RESTORE, ref, name)
    }.getOrNull()

    private suspend fun writeSealed(name: String): String {
        val key = PhoneKeys.backupKey() ?: throw RestoreError.NoKey()
        return write(name, BackupCipher.seal(export().toByteArray(), key))
    }

    private fun openWithPhoneKey(bytes: ByteArray): String {
        val key = PhoneKeys.backupKey() ?: throw RestoreError.OtherPhone()
        return try {
            String(BackupCipher.open(bytes, key), Charsets.UTF_8)
        } catch (e: BackupCipher.WrongKey) {
            throw RestoreError.OtherPhone()
        }
    }

    /** Writes (or rewrites) [name] in the backup folder, drops what the rotation says, returns its ref. */
    private fun write(name: String, bytes: ByteArray): String {
        val ref: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val own = listViaMediaStore()
            ref = writeViaMediaStore(own.firstOrNull { it.name == name }?.ref, name, bytes)
            val stale = BackupRotation.toDelete(own.map { it.name } + name).toSet()
            own.filter { it.name in stale && it.name != name }.forEach { runCatching { context.contentResolver.delete(Uri.parse(it.ref), null, null) } }
        } else {
            val file = File(appDir(), name).apply { writeBytes(bytes) }
            ref = file.absolutePath
            val files = appDir().listFiles()?.toList().orEmpty()
            val stale = BackupRotation.toDelete(files.map { it.name }).toSet()
            files.filter { it.name in stale && it.name != name }.forEach { it.delete() }
        }
        return ref
    }

    private class Found(val name: String, val ref: String, val modifiedAt: Long)

    private fun listAll(): List<Found> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listViaMediaStore()
        else appDir().listFiles()?.map { Found(it.name, it.absolutePath, it.lastModified()) }.orEmpty()

    private fun readRef(ref: String): ByteArray =
        if (ref.startsWith("content:")) context.contentResolver.openInputStream(Uri.parse(ref))?.use { SafeFiles.readLimitedBytes(it) } ?: error("нет файла")
        else File(ref).inputStream().use { SafeFiles.readLimitedBytes(it) }

    // ---- MediaStore (Android 10+) ----

    private fun writeViaMediaStore(existing: String?, name: String, bytes: ByteArray): String {
        val resolver = context.contentResolver
        val uri = existing?.let(Uri::parse) ?: resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, if (name.endsWith(".json")) "application/json" else "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            }
        ) ?: error("не удалось создать файл")
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("нет доступа к файлу")
        return uri.toString()
    }

    /** The copies in Downloads/BankAggregator this install can see. */
    private fun listViaMediaStore(): List<Found> {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_MODIFIED)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%$FOLDER%")
        val out = mutableListOf<Found>()
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                val modified = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)) * 1000
                out += Found(name, Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()).toString(), modified)
            }
        }
        return out
    }

    // Shot pictures live here; a copy's paths are re-pointed into them (B-30).
    private val cashbackShotsDir get() = File(context.filesDir, "cashback_shots")
    private val depositShotsDir get() = File(context.filesDir, "deposit_shots")

    private fun appDir(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }
    // ---- serialization ----

    suspend fun export(): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("exported_at", System.currentTimeMillis())

        val accountsJson = JSONArray()
        for (a in ledger.observeAccounts().first()) {
            accountsJson.put(JSONObject().apply {
                put("id", a.id); put("brandKey", a.brandKey); put("title", a.title)
                put("currency", a.currency)
                put("balanceMinor", a.balanceMinor); put("updatedAt", a.updatedAt)
                put("checkedAt", a.checkedAt ?: JSONObject.NULL); put("estimated", a.estimated)
            })
        }
        root.put("accounts", accountsJson)

        // v8: subscriptions (F-27), with the «Не подписка» marks.
        root.put("subscriptions", JSONArray().apply {
            subscriptions.all().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("title", s.title); put("priceMinor", s.priceMinor)
                    put("periodUnit", s.periodUnit); put("periodDays", s.periodDays); put("nextEpochDay", s.nextEpochDay)
                    put("brandKey", s.brandKey ?: JSONObject.NULL); put("payee", s.payee ?: JSONObject.NULL)
                    put("remind", s.remind); put("status", s.status)
                    put("previousPriceMinor", s.previousPriceMinor ?: JSONObject.NULL); put("createdAt", s.createdAt)
                    // v10: the subscription's own look (F-33), its picture travels like a bank's.
                    put("colorArgb", s.colorArgb ?: JSONObject.NULL); put("letters", s.letters ?: JSONObject.NULL)
                    put("textColorArgb", s.textColorArgb ?: JSONObject.NULL); put("logoFile", s.logoFile ?: JSONObject.NULL)
                    LogoBackup.encode(s.logoFile)?.let { put("logoPng", it) }
                })
            }
        })

        val txJson = JSONArray()
        for (t in ledger.observeTransactions(Int.MAX_VALUE).first()) {
            txJson.put(JSONObject().apply {
                put("id", t.id); put("brandKey", t.brandKey); put("accountId", t.accountId)
                put("amountMinor", t.amountMinor); put("currency", t.currency); put("kind", t.kind)
                put("category", t.category ?: JSONObject.NULL)
                put("counterparty", t.counterparty ?: JSONObject.NULL)
                put("postedAt", t.postedAt)
            })
        }
        root.put("transactions", txJson)

        val evJson = JSONArray()
        for (e in events.all()) {
            evJson.put(JSONObject().apply {
                put("packageName", e.packageName); put("brandKey", e.brandKey)
                put("title", e.title ?: JSONObject.NULL); put("text", e.text)
                put("postedAt", e.postedAt); put("dedupHash", e.dedupHash); put("parsed", e.parsed)
                put("contentHash", e.contentHash)
            })
        }
        root.put("events", evJson)

        val pkgs = JSONArray()
        settings.currentSelectedPackages().forEach { pkgs.put(it) }
        root.put("selectedPackages", pkgs)

        // v9: «Что такое Itogo» was shown — a restore must not bring the slides back (B-50).
        root.put("introShown", settings.introShown())
        // v9: apps answered «Не банк» — otherwise a restore asks about them again (B-57).
        candidates?.let { c ->
            root.put("notBanks", JSONArray().apply { c.ignored().forEach { put(it) } })
        }

        // Cashback — manually entered and not reconstructable from notifications,
        // so it must live in the backup.
        val cbJson = JSONArray()
        for (c in cashback.all()) {
            cbJson.put(JSONObject().apply {
                put("id", c.id); put("monthKey", c.monthKey); put("brandKey", c.brandKey)
                put("category", c.category); put("percent", c.percent); put("createdAt", c.createdAt)
                put("groupKey", c.groupKey)
            })
        }
        root.put("cashback", cbJson)

        // Shot metadata (status + bank + month). The image files themselves stay
        // on the device and are not embedded in the JSON.
        val shotJson = JSONArray()
        for (s in shots.all()) {
            shotJson.put(JSONObject().apply {
                put("id", s.id); put("monthKey", s.monthKey); put("imagePath", s.imagePath)
                put("brandKey", s.brandKey); put("status", s.status); put("createdAt", s.createdAt)
                put("fileName", s.fileName ?: JSONObject.NULL); put("linesJson", s.linesJson)
            })
        }
        root.put("shots", shotJson)

        // Deposits — manually entered / recognised, not reconstructable from
        // notifications, so they live in the backup.
        val depJson = JSONArray()
        for (d in deposits.all()) {
            depJson.put(JSONObject().apply {
                put("id", d.id); put("brandKey", d.brandKey); put("title", d.title ?: JSONObject.NULL)
                put("principalMinor", d.principalMinor); put("rateBp", d.rateBp)
                put("startEpochDay", d.startEpochDay); put("endEpochDay", d.endEpochDay)
                put("exitMinor", d.exitMinor ?: JSONObject.NULL)
                put("method", d.method); put("createdAt", d.createdAt)
            })
        }
        root.put("deposits", depJson)
        // v6: deposit top-ups (F-20).
        root.put("depositTopUps", JSONArray().apply {
            topUps.all().forEach { u ->
                put(JSONObject().apply { put("id", u.id); put("depositId", u.depositId); put("epochDay", u.epochDay); put("amountMinor", u.amountMinor) })
            }
        })

        // Deposit shot metadata (the images themselves stay on the device).
        val depShotJson = JSONArray()
        for (s in depositShots.all()) {
            depShotJson.put(JSONObject().apply {
                put("id", s.id); put("imagePath", s.imagePath); put("brandKey", s.brandKey)
                put("status", s.status); put("createdAt", s.createdAt)
                put("fileName", s.fileName ?: JSONObject.NULL); put("draftJson", s.draftJson)
            })
        }
        root.put("depositShots", depShotJson)

        // Cash currency history and cash rubles — entered by hand, backup only.
        val fxJson = JSONArray()
        val ops = fx.all()
        for (o in ops) {
            fxJson.put(JSONObject().apply {
                put("id", o.id); put("currency", o.currency); put("kind", o.kind)
                put("epochDay", o.epochDay); put("amountMinor", o.amountMinor)
                put("rubMinor", o.rubMinor); put("createdAt", o.createdAt)
            })
        }
        root.put("fxOperations", fxJson)
        fx.cash()?.let { c ->
            root.put("cash", JSONObject().apply { put("amountMinor", c.amountMinor); put("updatedAt", c.updatedAt) })
        }
        root.put("capitalParts", JSONArray().apply { settings.currentCapitalParts().forEach { put(it.key) } })
        // v6: the user's banks (F-22), in their order; own banks with their letters and colours.
        // v7: an own bank's picture goes along as base64 (F-25).
        banks?.let { b ->
            root.put("banks", JSONArray().apply {
                b.current().forEach { bank ->
                    put(BankStore.toJson(bank).apply { LogoBackup.encode(bank.logoFile)?.let { put("logoPng", it) } })
                }
            })
        }
        // The latest rate of every currency in use, so capital counts offline right after a restore.
        val used = (ops.map { it.currency } + ledger.observeAccounts().first().map { it.currency })
            .filter { it != "RUB" }.toSet()
        val ratesJson = JSONArray()
        for (code in used) {
            fx.latestOnOrBefore(code, Long.MAX_VALUE)?.let { r ->
                ratesJson.put(JSONObject().apply {
                    put("code", r.code); put("epochDay", r.epochDay); put("rubPerUnitMicro", r.rubPerUnitMicro)
                })
            }
        }
        root.put("rates", ratesJson)

        return root.toString(2)
    }

    /** Restore failure the UI explains in plain words. */
    sealed class RestoreError(message: String) : Exception(message) {
        class Corrupt : RestoreError("Файл повреждён, данные не изменены")
        class TooNew : RestoreError("Копия из более новой версии приложения")
        class NotOurs : RestoreError("Файл не похож на копию Itogo")
        class OtherPhone : RestoreError("Эта копия с другого телефона или сделана до сброса — открыть её нельзя")
        class NoKey : RestoreError("Не удалось зашифровать копию на этом телефоне")
    }

    /** Everything a backup holds, parsed in memory before anything is written. */
    private class Snapshot(
        val subscriptions: List<SubscriptionEntity>,
        val accounts: List<AccountEntity>,
        val transactions: List<TransactionEntity>,
        val events: List<EventEntity>,
        val selectedPackages: Set<String>?,
        val cashback: List<CashbackEntity>,
        val shots: List<CashbackShotEntity>,
        val deposits: List<DepositEntity>,
        val depositTopUps: List<DepositTopUpEntity> = emptyList(),
        val depositShots: List<DepositShotEntity>,
        val fxOperations: List<FxOperationEntity>,
        val cash: CashEntity?,
        val capitalParts: Set<CapitalPart>?,
        val rates: List<FxRateEntity>,
        val banks: List<BankBrand>? = null,
        val logos: Map<String, ByteArray> = emptyMap(),
        /** Subscription pictures by subscription id (F-33). */
        val subscriptionLogos: Map<String, ByteArray> = emptyMap(),
        val notBanks: Set<String>? = null,
        val introShown: Boolean = false
    )

    /**
     * Replaces all local data with [json]. The whole file is parsed first; if any
     * part is broken nothing is touched ([RestoreError.Corrupt]). Then the tables
     * are cleared and refilled in one transaction. Older format versions get
     * defaults for missing fields; a newer one is refused ([RestoreError.TooNew]).
     */
    suspend fun import(json: String) {
        val snapshot = try {
            parse(json)
        } catch (e: RestoreError) {
            throw e
        } catch (e: Exception) {
            throw RestoreError.Corrupt()
        }
        // Subscription pictures go back into the app's files first; a path from the file itself is never trusted (B-30).
        val logoDir = File(context.filesDir, "bank-logos")
        val restoredSubscriptions = snapshot.subscriptions.map { s ->
            val own = snapshot.subscriptionLogos[s.id]?.let { bytes -> runCatching { LogoBackup.write(logoDir, s.id, bytes) }.getOrNull() }
            s.copy(logoFile = own ?: s.logoFile?.takeIf { SafeFiles.inside(logoDir, it) != null })
        }
        db.withTransaction {
            ledger.clearTransactions()
            ledger.clearAccounts()
            events.clear()
            cashback.clear()
            shots.clear()
            deposits.clear()
            topUps.clear()
            depositShots.clear()
            fx.clear()
            fx.clearCash()
            subscriptions.clear()
            restoredSubscriptions.forEach { subscriptions.upsert(it) }
            snapshot.accounts.forEach { ledger.upsertAccount(it) }
            snapshot.transactions.forEach { ledger.insertTransaction(it) }
            snapshot.events.forEach { events.insert(it) }
            cashback.upsertAll(snapshot.cashback)
            snapshot.shots.forEach { shots.upsert(it) }
            snapshot.deposits.forEach { deposits.upsert(it) }
            snapshot.depositTopUps.forEach { topUps.upsert(it) }
            snapshot.depositShots.forEach { depositShots.upsert(it) }
            snapshot.fxOperations.forEach { fx.upsert(it) }
            snapshot.cash?.let { fx.setCash(it) }
            if (snapshot.rates.isNotEmpty()) fx.upsertRates(snapshot.rates)
        }
        snapshot.selectedPackages?.let { settings.setSelectedPackages(it) }
        if (snapshot.introShown) settings.markIntroShown()
        // «Не банк» survives a restore: what was answered stays answered (B-57).
        candidates?.let { store ->
            snapshot.notBanks?.let { saved ->
                (store.ignored() - saved).forEach { store.unignore(it) }
                saved.forEach { store.ignore(it) }
            }
        }
        snapshot.capitalParts?.let { settings.setCapitalParts(it) }
        snapshot.banks?.let { list ->
            // Pictures from the copy are written back into the app's files (F-25).
            val restored = list.map { b ->
                snapshot.logos[b.key]?.let { bytes ->
                    runCatching { LogoBackup.write(logoDir, b.key, bytes) }.getOrNull()?.let { b.copy(logoFile = it) }
                    // A path from the file itself is never trusted (B-30).
                } ?: b.copy(logoFile = b.logoFile?.takeIf { SafeFiles.inside(logoDir, it) != null })
            }
            banks?.replaceAll(restored)
        }
    }

    private fun parse(json: String): Snapshot {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        if (version > FORMAT_VERSION) throw RestoreError.TooNew()

        fun JSONObject.objects(key: String): List<JSONObject> =
            optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } } ?: emptyList()

        return Snapshot(
            subscriptions = root.objects("subscriptions").map { o ->
                SubscriptionEntity(
                    id = o.getString("id"), title = o.getString("title"), priceMinor = o.getLong("priceMinor"),
                    periodUnit = o.getString("periodUnit"), periodDays = o.optInt("periodDays", 0),
                    nextEpochDay = o.getLong("nextEpochDay"), brandKey = o.optStringOrNull("brandKey"),
                    payee = o.optStringOrNull("payee"), remind = o.optBoolean("remind", false),
                    status = o.optString("status", "ACTIVE"),
                    previousPriceMinor = if (o.isNull("previousPriceMinor") || !o.has("previousPriceMinor")) null else o.getLong("previousPriceMinor"),
                    createdAt = o.optLong("createdAt", 0L),
                    // Copies before v10 have no look: the title gives the default.
                    colorArgb = if (o.isNull("colorArgb") || !o.has("colorArgb")) null else o.getLong("colorArgb"),
                    letters = o.optStringOrNull("letters"),
                    textColorArgb = if (o.isNull("textColorArgb") || !o.has("textColorArgb")) null else o.getLong("textColorArgb"),
                    logoFile = o.optStringOrNull("logoFile")
                )
            },
            accounts = root.objects("accounts").map { o ->
                AccountEntity(
                    id = o.getString("id"), brandKey = o.getString("brandKey"),
                    title = o.getString("title"),
                    currency = o.getString("currency"), balanceMinor = o.getLong("balanceMinor"),
                    updatedAt = o.getLong("updatedAt"),
                    // Copies before B-29: a balance the bank told is exact as of its update.
                    checkedAt = if (o.has("checkedAt")) (if (o.isNull("checkedAt")) null else o.getLong("checkedAt"))
                        else o.getLong("updatedAt").takeIf { o.getLong("balanceMinor") != 0L },
                    estimated = o.optBoolean("estimated", false)
                )
            },
            transactions = root.objects("transactions").map { o ->
                TransactionEntity(
                    id = o.getString("id"), brandKey = o.getString("brandKey"),
                    accountId = o.getString("accountId"), amountMinor = o.getLong("amountMinor"),
                    currency = o.getString("currency"), kind = o.getString("kind"),
                    category = o.optStringOrNull("category"),
                    counterparty = o.optStringOrNull("counterparty"),
                    postedAt = o.getLong("postedAt")
                )
            },
            events = root.objects("events").map { o ->
                val hash = o.getString("dedupHash")
                EventEntity(
                    packageName = o.getString("packageName"), brandKey = o.getString("brandKey"),
                    title = o.optStringOrNull("title"), text = o.getString("text"),
                    postedAt = o.getLong("postedAt"), dedupHash = hash,
                    parsed = o.optBoolean("parsed", false),
                    contentHash = o.optString("contentHash", hash)
                )
            },
            selectedPackages = root.optJSONArray("selectedPackages")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            },
            notBanks = root.optJSONArray("notBanks")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            },
            introShown = root.optBoolean("introShown", false),
            // "favoriteGroups" from older copies is ignored: the home card picks itself.
            cashback = root.objects("cashback").map { o ->
                CashbackEntity(
                    id = o.getString("id"), monthKey = o.getString("monthKey"),
                    brandKey = o.getString("brandKey"), category = o.getString("category"),
                    percent = o.getDouble("percent"), createdAt = o.getLong("createdAt"),
                    groupKey = o.optString("groupKey", CategoryGroups.OTHER)
                )
            },
            shots = root.objects("shots").map { o ->
                CashbackShotEntity(
                    id = o.getString("id"), monthKey = o.getString("monthKey"),
                    imagePath = SafeFiles.rebase(cashbackShotsDir, o.getString("imagePath")), brandKey = o.getString("brandKey"),
                    status = o.getString("status"), createdAt = o.getLong("createdAt"),
                    fileName = o.optStringOrNull("fileName"), linesJson = o.optString("linesJson", "[]")
                )
            },
            depositTopUps = root.objects("depositTopUps").map { o ->
                DepositTopUpEntity(o.getString("id"), o.getString("depositId"), o.getLong("epochDay"), o.getLong("amountMinor"))
            },
            deposits = root.objects("deposits").map { o ->
                DepositEntity(
                    id = o.getString("id"), brandKey = o.getString("brandKey"),
                    title = o.optStringOrNull("title"),
                    principalMinor = o.getLong("principalMinor"), rateBp = o.getInt("rateBp"),
                    startEpochDay = o.getLong("startEpochDay"), endEpochDay = o.getLong("endEpochDay"),
                    exitMinor = if (o.isNull("exitMinor")) null else o.getLong("exitMinor"),
                    method = o.optString("method", "LINEAR"), createdAt = o.getLong("createdAt")
                )
            },
            depositShots = root.objects("depositShots").map { o ->
                DepositShotEntity(
                    id = o.getString("id"), imagePath = SafeFiles.rebase(depositShotsDir, o.getString("imagePath")),
                    brandKey = o.getString("brandKey"), status = o.getString("status"),
                    createdAt = o.getLong("createdAt"), fileName = o.optStringOrNull("fileName"),
                    draftJson = o.optString("draftJson", "{}")
                )
            },
            // v5: cash currency, cash rubles, capital parts, rates. Older copies have none.
            fxOperations = root.objects("fxOperations").map { o ->
                FxOperationEntity(
                    id = o.getString("id"), currency = o.getString("currency"), kind = o.getString("kind"),
                    epochDay = o.getLong("epochDay"), amountMinor = o.getLong("amountMinor"),
                    rubMinor = o.getLong("rubMinor"), createdAt = o.getLong("createdAt")
                )
            },
            cash = root.optJSONObject("cash")?.let { o ->
                CashEntity(amountMinor = o.getLong("amountMinor"), updatedAt = o.getLong("updatedAt"))
            },
            capitalParts = root.optJSONArray("capitalParts")?.let { arr ->
                (0 until arr.length()).mapNotNull { CapitalPart.fromKey(arr.getString(it)) }.toSet()
            },
            rates = root.objects("rates").map { o ->
                FxRateEntity(o.getString("code"), o.getLong("epochDay"), o.getLong("rubPerUnitMicro"))
            },
            banks = root.optJSONArray("banks")?.let { arr ->
                (0 until arr.length()).map { BankStore.fromJson(arr.getJSONObject(it)) }
            },
            logos = root.objects("banks").mapNotNull { o ->
                LogoBackup.decode(o.optString("logoPng"))?.let { o.getString("key") to it }
            }.toMap(),
            subscriptionLogos = root.objects("subscriptions").mapNotNull { o ->
                LogoBackup.decode(o.optString("logoPng"))?.let { o.getString("id") to it }
            }.toMap()
        )
    }

    private companion object {
        // v10: subscription look and pictures (F-33); v7: own bank pictures; v6: banks; v5: cash currency, cash, capital parts, rates (v4: deposits; v3: cashback groupKey).
        const val FORMAT_VERSION = 10
        const val FOLDER = "BankAggregator"
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key)
