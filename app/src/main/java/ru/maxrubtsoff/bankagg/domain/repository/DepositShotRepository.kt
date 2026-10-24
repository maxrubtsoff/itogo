package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.DepositShot
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus

/** Stored deposit screenshots the user uploaded, awaiting review and saving. */
interface DepositShotRepository {
    fun observeAll(): Flow<List<DepositShot>>
    suspend fun get(id: String): DepositShot?
    /** One shot, live; null once it is deleted. */
    fun observe(id: String): Flow<DepositShot?>
    /** Persists a new shot (image already saved to [DepositShot.imagePath]). */
    suspend fun add(shot: DepositShot)
    suspend fun update(shot: DepositShot)
    suspend fun setStatus(id: String, status: ShotStatus)
    /** Deletes the shot row and its image file. */
    suspend fun delete(id: String)
    /** Deletes only the row and keeps the image, so the shot can be restored ("Вернуть"). */
    suspend fun deleteKeepingImage(id: String)
    /** Removes an image left by [deleteKeepingImage] once the shot is gone for good. */
    suspend fun deleteImage(imagePath: String)
}
