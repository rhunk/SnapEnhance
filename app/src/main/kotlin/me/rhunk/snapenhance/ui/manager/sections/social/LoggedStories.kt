package me.rhunk.snapenhance.ui.manager.sections.social

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.DownloadCallback
import me.rhunk.snapenhance.common.data.FileType
import me.rhunk.snapenhance.common.data.StoryData
import me.rhunk.snapenhance.common.data.download.*
import me.rhunk.snapenhance.common.util.ktx.longHashCode
import me.rhunk.snapenhance.common.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.core.util.media.PreviewUtils
import me.rhunk.snapenhance.download.DownloadProcessor
import me.rhunk.snapenhance.ui.util.Dialog
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.absoluteValue

@OptIn(ExperimentalCoilApi::class)
@Composable
fun LoggedStories(
    context: RemoteSideContext,
    userId: String
) {
    val stories = remember {
        mutableStateListOf<StoryData>()
    }
    val friendInfo = remember {
        context.modDatabase.getFriendInfo(userId)
    }
    val httpClient = remember { OkHttpClient() }
    var lastStoryTimestamp by remember { mutableLongStateOf(Long.MAX_VALUE) }

    var selectedStory by remember { mutableStateOf<StoryData?>(null) }
    var coilCacheFile by remember { mutableStateOf<File?>(null) }

    selectedStory?.let { story ->
        Dialog(onDismissRequest = {
            selectedStory = null
        }) {
            Card(
                modifier = Modifier
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "Posted on ${story.postedAt.let {
                        DateFormat.getDateTimeInstance().format(Date(it))
                    }}")
                    Text(text = "Created at ${story.createdAt.let {
                        DateFormat.getDateTimeInstance().format(Date(it))
                    }}")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Button(onClick = {
                            context.androidContext.externalCacheDir?.let { cacheDir ->
                                val cacheFile = coilCacheFile ?: run {
                                    context.shortToast("Failed to get file")
                                    return@Button
                                }
                                val targetFile = File(cacheDir, cacheFile.name)
                                cacheFile.copyTo(targetFile, overwrite = true)
                                context.androidContext.startActivity(Intent().apply {
                                    action = Intent.ACTION_VIEW
                                    setDataAndType(
                                        FileProvider.getUriForFile(
                                            context.androidContext,
                                            "me.rhunk.snapenhance.fileprovider",
                                            targetFile
                                        ),
                                        FileType.fromFile(targetFile).mimeType
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        }) {
                            Text(text = "Open")
                        }

                        Button(onClick = {
                            val mediaAuthor = friendInfo?.mutableUsername ?: userId
                            val uniqueHash = selectedStory?.url?.longHashCode()?.absoluteValue?.toString(16) ?: UUID.randomUUID().toString()

                            DownloadProcessor(
                                remoteSideContext = context,
                                callback = object: DownloadCallback.Default() {
                                    override fun onSuccess(outputPath: String?) {
                                        context.shortToast("Downloaded to $outputPath")
                                    }

                                    override fun onFailure(message: String?, throwable: String?) {
                                        context.shortToast("Failed to download $message")
                                    }
                                }
                            ).enqueue(DownloadRequest(
                                inputMedias = arrayOf(
                                    InputMedia(
                                        content = story.url,
                                        type = DownloadMediaType.REMOTE_MEDIA,
                                        encryption = story.key?.let { it to story.iv!! }?.toKeyPair()
                                    )
                                )
                            ), DownloadMetadata(
                                mediaIdentifier = uniqueHash,
                                outputPath = createNewFilePath(
                                    context.config.root,
                                    uniqueHash,
                                    MediaDownloadSource.STORY_LOGGER,
                                    mediaAuthor,
                                    story.createdAt
                                ),
                                iconUrl = null,
                                mediaAuthor = friendInfo?.mutableUsername ?: userId,
                                downloadSource = MediaDownloadSource.STORY_LOGGER.key
                            ))
                        }) {
                            Text(text = "Download")
                        }
                    }
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(stories) { story ->
            var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
            val uniqueHash = remember { story.url.hashCode().absoluteValue.toString(16) }

            fun openDiskCacheSnapshot(snapshot: DiskCache.Snapshot): Boolean {
                runCatching {
                    val mediaList = mutableMapOf<SplitMediaAssetType, ByteArray>()

                    snapshot.data.toFile().inputStream().use { inputStream ->
                        MediaDownloaderHelper.getSplitElements(inputStream) { type, splitInputStream ->
                            mediaList[type] = splitInputStream.readBytes()
                        }
                    }

                    val originalMedia = mediaList[SplitMediaAssetType.ORIGINAL] ?: return@runCatching false
                    val overlay = mediaList[SplitMediaAssetType.OVERLAY]

                    var bitmap: Bitmap? = PreviewUtils.createPreview(originalMedia, isVideo = FileType.fromByteArray(originalMedia).isVideo)

                    overlay?.also {
                        bitmap = PreviewUtils.mergeBitmapOverlay(bitmap!!, BitmapFactory.decodeByteArray(it, 0, it.size))
                    }

                    imageBitmap = bitmap?.asImageBitmap()
                    return true
                }
                return false
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    withTimeout(10000L) {
                        context.imageLoader.diskCache?.openSnapshot(uniqueHash)?.let {
                            openDiskCacheSnapshot(it)
                            it.close()
                            return@withTimeout
                        }

                        val response = httpClient.newCall(Request(
                            url = story.url.toHttpUrl()
                        )).execute()
                        response.body.byteStream().use {
                            val decrypted = story.key?.let { _ ->
                                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(story.key, "AES"), IvParameterSpec(story.iv))
                                CipherInputStream(it, cipher)
                            } ?: it

                            context.imageLoader.diskCache?.openEditor(uniqueHash)?.apply {
                                data.toFile().outputStream().use { fos ->
                                    decrypted.copyTo(fos)
                                }
                                commitAndOpenSnapshot()?.use { snapshot ->
                                    openDiskCacheSnapshot(snapshot)
                                    snapshot.close()
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        selectedStory = story
                        coilCacheFile = context.imageLoader.diskCache?.openSnapshot(uniqueHash).use {
                            it?.data?.toFile()
                        }
                    }
                    .heightIn(min = 128.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                imageBitmap?.let {
                    Card {
                        Image(
                            bitmap = it,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                        )
                    }
                } ?: run {
                    CircularProgressIndicator()
                }
            }
        }
        item {
            LaunchedEffect(Unit) {
                context.messageLogger.getStories(userId, lastStoryTimestamp, 20).also { result ->
                    stories.addAll(result.values)
                    result.keys.minOrNull()?.let {
                        lastStoryTimestamp = it
                    }
                }
            }
        }
    }
}