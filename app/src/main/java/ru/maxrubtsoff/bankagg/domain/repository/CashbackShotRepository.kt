package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus

/** Stored cashback screenshots the user uploaded for a given month. */
interface CashbackShotRepository {
    fun observeMonth(monthKey: String): Flow<List<CashbackShot>>
    suspend fun get(id: String): CashbackShot?
    /** One shot, live; null once it is deleted. */
    fun observe(id: String): Flow<CashbackShot?>
    /** Persists a new shot (image already saved to [CashbackShot.imagePath]). */
    suspend fun add(shot: CashbackShot)
    suspend fun update(shot: CashbackShot)
    suspend fun setStatus(id: String, status: ShotStatus)
    /** Deletes the shot row and its image file. */
    suspend fun delete(id: String)
    /** Deletes only the row and keeps the image, so the shot can be restored ("Вернуть"). */
    suspend fun deleteKeepingImage(id: String)
    /** Removes an image left by [deleteKeepingImage] once the shot is gone for good. */
    suspend fun deleteImage(imagePath: String)
}
