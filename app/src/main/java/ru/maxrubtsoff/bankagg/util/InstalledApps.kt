package ru.maxrubtsoff.bankagg.util

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import ru.maxrubtsoff.bankagg.domain.model.BankBrand

/**
 * Reads icons and installation state for the specific bank packages declared
 * in the manifest <queries>. Needs no QUERY_ALL_PACKAGES permission because
 * each package is named explicitly there.
 */
object InstalledApps {

    /** First installed package of the brand, or null if none is installed. */
    fun installedPackage(context: Context, brand: BankBrand): String? {
        val pm = context.packageManager
        return brand.packages.firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    /** The app's name as the launcher shows it, if the system lets us see it. */
    fun labelOf(context: Context, pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun isInstalled(context: Context, brand: BankBrand): Boolean =
        installedPackage(context, brand) != null

    /**
     * The real launcher icon for the brand's installed app, if available.
     * [sizePx] scales it down (widgets: RemoteViews have a bitmap memory limit).
     */
    fun iconFor(context: Context, brand: BankBrand, sizePx: Int? = null): Bitmap? {
        val pkg = installedPackage(context, brand) ?: return null
        return runCatching {
            val d = context.packageManager.getApplicationIcon(pkg)
            if (sizePx != null) d.toBitmap(sizePx, sizePx) else d.toBitmap()
        }.getOrNull()
    }
}
