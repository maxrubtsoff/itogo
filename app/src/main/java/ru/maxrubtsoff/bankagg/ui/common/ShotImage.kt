package ru.maxrubtsoff.bankagg.ui.common

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import ru.maxrubtsoff.bankagg.data.ocr.ShotImageFiles

/** A stored screenshot to show: Coil opens it through [Fetcher], since the file is encrypted (F-26). */
data class ShotImage(val path: String) {

    class Fetcher(private val shot: ShotImage, private val options: Options) : coil.fetch.Fetcher {
        override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
            val bytes = ShotImageFiles.read(shot.path) ?: error("no picture")
            SourceResult(ImageSource(Buffer().write(bytes), options.context), mimeType = null, dataSource = DataSource.DISK)
        }

        class Factory : coil.fetch.Fetcher.Factory<ShotImage> {
            override fun create(data: ShotImage, options: Options, imageLoader: ImageLoader) = Fetcher(data, options)
        }
    }

    class Key : Keyer<ShotImage> {
        override fun key(data: ShotImage, options: Options) = data.path
    }
}
