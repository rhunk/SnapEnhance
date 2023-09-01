package me.rhunk.snapenhance.ui.util

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.RemoteSideContext

@Composable
fun BitmojiImage(context: RemoteSideContext, modifier: Modifier = Modifier, size: Int = 48, url: String?) {
    Image(
        painter = rememberAsyncImagePainter(
            model = ImageRequestHelper.newBitmojiImageRequest(
                context.androidContext,
                url
            ),
            imageLoader = context.imageLoader
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .requiredWidthIn(min = 0.dp, max = size.dp)
            .height(size.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(modifier)
    )
}

fun ImageRequest.Builder.cacheKey(key: String?) = apply {
    memoryCacheKey(key)
    diskCacheKey(key)
}

object ImageRequestHelper {
    fun newBitmojiImageRequest(context: Context, url: String?) = ImageRequest.Builder(context)
        .data(url)
        .fallback(R.drawable.bitmoji_blank)
        .precision(Precision.INEXACT)
        .crossfade(true)
        .cacheKey(url)
        .build()

    fun newDownloadPreviewImageRequest(context: Context, filePath: String?) = ImageRequest.Builder(context)
        .data(filePath)
        .cacheKey(filePath)
        .memoryCacheKey(filePath)
        .crossfade(true)
        .crossfade(200)
        .build()
}