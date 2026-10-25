package ru.maxrubtsoff.bankagg.data.settings

import android.content.Context
import android.util.Base64
import ru.maxrubtsoff.bankagg.data.backup.BackupCipher
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.domain.repository.CandidateRepository

/** Candidates in small preferences: one entry per app, its text kept only until answered (F-23). */
class CandidateStore(context: Context) : CandidateRepository {

    private val prefs = context.applicationContext.getSharedPreferences("bank_candidates", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _list = MutableStateFlow(read())
    override val candidates: Flow<List<BankCandidate>> = _list.asStateFlow()

    private val _ignored = MutableStateFlow(ignoredNow())
    override val ignoredApps: Flow<Set<String>> = _ignored.asStateFlow()

    override suspend fun current(): List<BankCandidate> = _list.value

    override suspend fun put(candidate: BankCandidate) = lock.withLock {
        write(_list.value.filterNot { it.packageName == candidate.packageName } + candidate)
    }

    override suspend fun remove(packageName: String) = lock.withLock {
        write(_list.value.filterNot { it.packageName == packageName })
    }

    override suspend fun ignore(packageName: String) = lock.withLock {
        setIgnored(ignoredNow() + packageName)
        write(_list.value.filterNot { it.packageName == packageName })
    }

    override suspend fun unignore(packageName: String) = lock.withLock {
        setIgnored(ignoredNow() - packageName)
    }

    private fun setIgnored(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_IGNORED, packages).apply()
        _ignored.value = packages
    }

    override suspend fun ignored(): Set<String> = ignoredNow()

    private fun ignoredNow(): Set<String> = prefs.getStringSet(KEY_IGNORED, emptySet())?.toSet() ?: emptySet()

    private fun write(list: List<BankCandidate>) {
        val json = JSONArray(list.map { c ->
            JSONObject().put("pkg", c.packageName).put("app", c.appName).put("title", c.title ?: JSONObject.NULL)
                .put("text", c.text).put("at", c.postedAt).put("guess", c.guessKey ?: JSONObject.NULL)
        }).toString()
        // The quoted bank text is sealed with the phone's key (U-34); plain only where the Keystore doesn't work.
        val sealed = PhoneKeys.backupKey()?.let { Base64.encodeToString(BackupCipher.seal(json.toByteArray(), it), Base64.NO_WRAP) }
        prefs.edit().apply {
            if (sealed != null) putString(KEY_SEALED, sealed).remove(KEY_LIST) else putString(KEY_LIST, json)
        }.apply()
        _list.value = list
    }

    private fun read(): List<BankCandidate> {
        val raw = prefs.getString(KEY_SEALED, null)?.let { sealed ->
            runCatching { String(BackupCipher.open(Base64.decode(sealed, Base64.NO_WRAP), PhoneKeys.backupKey()!!)) }.getOrNull()
        } ?: prefs.getString(KEY_LIST, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                BankCandidate(
                    packageName = o.getString("pkg"),
                    appName = o.getString("app"),
                    title = if (o.isNull("title")) null else o.getString("title"),
                    text = o.getString("text"),
                    postedAt = o.getLong("at"),
                    guessKey = if (o.isNull("guess")) null else o.getString("guess")
                )
            }.getOrNull()
        }
    }

    private companion object {
        const val KEY_LIST = "list"
        const val KEY_SEALED = "sealed"
        const val KEY_IGNORED = "ignored"
    }
}
