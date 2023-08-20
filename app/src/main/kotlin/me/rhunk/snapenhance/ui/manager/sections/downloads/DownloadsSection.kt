package me.rhunk.snapenhance.ui.manager.sections.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.download.data.DownloadObject
import me.rhunk.snapenhance.download.data.MediaFilter
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.BitmojiImage
import me.rhunk.snapenhance.ui.util.ImageRequestHelper

class DownloadsSection : Section() {
    private val loadedDownloads = mutableStateOf(mapOf<Int, DownloadObject>())
    private var currentFilter = mutableStateOf(MediaFilter.NONE)

    override fun onResumed() {
        super.onResumed()
        loadByFilter(currentFilter.value)
    }

    private fun loadByFilter(filter: MediaFilter) {
        this.currentFilter.value = filter
        synchronized(loadedDownloads) {
            loadedDownloads.value = context.downloadTaskManager.queryFirstTasks(filter)
        }
    }

    private fun lazyLoadFromIndex(lastIndex: Int) {
        synchronized(loadedDownloads) {
            loadedDownloads.value = loadedDownloads.value.toMutableMap().also {
                val lastVisible = loadedDownloads.value.values.elementAt(lastIndex)
                it += context.downloadTaskManager.queryTasks(
                    from = lastVisible.downloadId,
                    filter = currentFilter.value
                )
            }
        }
    }

    @Composable
    private fun FilterList() {
        val coroutineScope = rememberCoroutineScope()
        var showMenu by remember { mutableStateOf(false) }
        IconButton(onClick = { showMenu = !showMenu}) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            MediaFilter.values().toList().forEach { filter ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                modifier = Modifier.padding(end = 16.dp),
                                selected = (currentFilter.value == filter),
                                onClick = null
                            )
                            Text(filter.name, modifier = Modifier.weight(1f))
                        }
                   },
                    onClick = {
                        coroutineScope.launch {
                            loadByFilter(filter)
                            showMenu = false
                        }
                    }
                )
            }
        }
    }

    @Composable
    override fun TopBarActions(rowScope: RowScope) {
        FilterList()
    }

    @Composable
    private fun DownloadItem(download: DownloadObject) {
        Card(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
        ) {
            Box(modifier = Modifier.height(100.dp)) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequestHelper.newDownloadPreviewImageRequest(
                            context.androidContext,
                            download.outputFile
                        ),
                        imageLoader = context.imageLoader
                    ),
                    modifier = Modifier
                        .matchParentSize()
                        .blur(12.dp),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth
                )

                Row(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    //info card
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.background,
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BitmojiImage(context = context, url = download.metadata.iconUrl, size = 48)
                        Column(
                            modifier = Modifier
                                .padding(start = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = download.metadata.mediaDisplayType ?: "",
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = download.metadata.mediaDisplaySource ?: "",
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    //action buttons
                    Row(
                        modifier = Modifier
                            .padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null
                            )
                        }
                        //open
                        FilledIconButton(onClick = {
                            val fileType = runCatching {
                                context.androidContext.contentResolver.openInputStream(Uri.parse(download.outputFile))?.use { input ->
                                    FileType.fromInputStream(input)
                                }
                            }.getOrNull() ?: FileType.UNKNOWN

                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(download.outputFile), fileType.mimeType)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.androidContext.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val scrollState = rememberLazyListState()

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(loadedDownloads.value.size) { index ->
                DownloadItem(loadedDownloads.value.values.elementAt(index))
            }

            item {
                Spacer(Modifier.height(20.dp))
                if (loadedDownloads.value.isEmpty()) {
                    Text(text = "No downloads", fontSize = 20.sp, modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp), textAlign = TextAlign.Center)
                }
                LaunchedEffect(true) {
                    val lastItemIndex = (loadedDownloads.value.size - 1).takeIf { it >= 0 } ?: return@LaunchedEffect
                    lazyLoadFromIndex(lastItemIndex)
                    scrollState.animateScrollToItem(lastItemIndex)
                }
            }
        }
    }
}