package ru.maxrubtsoff.bankagg.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.data.local.CashbackShotDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.repository.CashbackShotRepository
import ru.maxrubtsoff.bankagg.data.backup.SafeFiles
import java.io.File

class CashbackShotRepositoryImpl(
    private val dao: CashbackShotDao,
    /** Where shot pictures live: only files inside it are ever deleted (B-30). */
    private val imageDir: File
) : CashbackShotRepository {

    override fun observeMonth(monthKey: String): Flow<List<CashbackShot>> =
        dao.observeMonth(monthKey).map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): CashbackShot? = dao.get(id)?.toDomain()

    override fun observe(id: String): Flow<CashbackShot?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun add(shot: CashbackShot) = dao.upsert(shot.toEntity())

    override suspend fun update(shot: CashbackShot) = dao.upsert(shot.toEntity())

    override suspend fun setStatus(id: String, status: ShotStatus) =
        dao.setStatus(id, status.name)

    override suspend fun delete(id: String) {
        // Remove the image file too, so deleting a shot leaves nothing behind.
        dao.get(id)?.let { entity ->
            runCatching { SafeFiles.deleteInside(imageDir, entity.imagePath) }
        }
        dao.deleteById(id)
    }

    override suspend fun deleteKeepingImage(id: String) = dao.deleteById(id)

    override suspend fun deleteImage(imagePath: String) {
        runCatching { SafeFiles.deleteInside(imageDir, imagePath) }
    }
}
