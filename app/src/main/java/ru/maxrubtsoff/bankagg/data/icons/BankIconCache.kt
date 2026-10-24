package ru.maxrubtsoff.bankagg.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.IconRefresh
import ru.maxrubtsoff.bankagg.util.InstalledApps
import java.io.File

/**
 * Bank app icons saved as small PNGs in the app's files (B-18). On launch they
 * are read from disk before the first frame — no letters flashing instead of
 * logos, and no package-manager calls while screens animate. A background pass
 * re-reads them from the system at most once a day, or when a bank app was
 * installed or removed.
 */
class BankIconCache(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "bank-icons")
    private val meta = File(dir, "saved.txt")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** The launch check and the first resume's check would otherwise race on the files. */
    private val busy = Mutex()

    private val _icons = MutableStateFlow<Map<BankBrand, Bitmap>>(emptyMap())
    /** Installed banks' icons; a bank without an entry has no app on the phone. */
    val icons: StateFlow<Map<BankBrand, Bitmap>> = _icons.asStateFlow()

    /** Reads the saved icons (a few small files), then refreshes them in the background if they're old. */
    fun start() {
        _icons.value = readSaved()
        scope.launch { busy.withLock { refreshIfStale() } }
    }

    /** Checks again (on return to the app) — cheap when nothing changed. */
    fun refresh() {
        scope.launch { busy.withLock { refreshIfStale() } }
    }

    private fun readSaved(): Map<BankBrand, Bitmap> =
        BankBrand.all.mapNotNull { b ->
            val f = File(dir, "${b.key}.png")
            if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)?.let { b to it }
        }.toMap()

    private fun refreshIfStale() {
        val installed = BankBrand.all.filter { InstalledApps.isInstalled(appContext, it) }
        val lines = runCatching { meta.readLines() }.getOrNull()
        val savedAt = lines?.firstOrNull()?.toLongOrNull()
        val savedBanks = lines?.drop(1)?.toSet()
        val now = System.currentTimeMillis()
        if (!IconRefresh.isStale(savedAt, now, savedBanks, installed.map { it.key }.toSet())) return

        dir.mkdirs()
        val fresh = installed.mapNotNull { b ->
            InstalledApps.iconFor(appContext, b, ICON_PX)?.let { bmp ->
                runCatching { File(dir, "${b.key}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                b to bmp
            }
        }.toMap()
        BankBrand.all.filter { it !in fresh }.forEach { File(dir, "${it.key}.png").delete() }
        meta.writeText((listOf(now.toString()) + installed.map { it.key }).joinToString("\n"))
        _icons.value = fresh
    }

    private companion object {
        /** ~48 dp at xxhdpi; plenty for every place a logo is drawn. */
        const val ICON_PX = 144
    }
}
