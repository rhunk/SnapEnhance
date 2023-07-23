package me.rhunk.snapenhance.ui.download

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.download.data.PendingDownload
import me.rhunk.snapenhance.download.enums.DownloadStage
import me.rhunk.snapenhance.util.snap.PreviewUtils
import java.io.File
import java.io.FileInputStream
import java.net.URL
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext

class DownloadListAdapter(
    private val activity: DownloadManagerActivity,
    private val downloadList: MutableList<PendingDownload>
): Adapter<DownloadListAdapter.ViewHolder>() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val previewJobs = mutableMapOf<Int, Job>()

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val bitmojiIcon: ImageView = view.findViewById(R.id.bitmoji_icon)
        val title: TextView = view.findViewById(R.id.item_title)
        val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        val status: TextView = view.findViewById(R.id.item_status)
        val actionButton: Button = view.findViewById(R.id.item_action_button)
        val radius by lazy {
            view.context.resources.getDimensionPixelSize(R.dimen.download_manager_item_preview_radius)
        }
        val viewWidth by lazy {
            view.resources.displayMetrics.widthPixels
        }
        val viewHeight by lazy {
            view.layoutParams.height
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.download_manager_item, parent, false))
    }

    override fun getItemCount(): Int {
        return downloadList.size
    }

    @SuppressLint("Recycle")
    private suspend fun handlePreview(download: PendingDownload, holder: ViewHolder) {
        download.outputFile?.let {
            val uri = Uri.parse(it)
            runCatching {
                if (uri.scheme == "content") {
                    val fileType = activity.contentResolver.openInputStream(uri)!!.use { stream ->
                        FileType.fromInputStream(stream)
                    }
                    fileType to activity.contentResolver.openInputStream(uri)
                } else {
                    FileType.fromFile(File(it)) to FileInputStream(it)
                }
            }.getOrNull()
        }?.also { (fileType, assetStream) ->
            val previewBitmap = assetStream?.use { stream ->
                //don't preview files larger than 30MB
                if (stream.available() > 30 * 1024 * 1024) return@also

                val tempFile = File.createTempFile("preview", ".${fileType.fileExtension}")
                tempFile.outputStream().use { output ->
                    stream.copyTo(output)
                }
                runCatching {
                    PreviewUtils.createPreviewFromFile(tempFile)?.let { preview ->
                        val offsetY = (preview.height / 2 - holder.viewHeight / 2).coerceAtLeast(0)

                        Bitmap.createScaledBitmap(
                            Bitmap.createBitmap(
                                preview, 0, offsetY,
                                preview.width.coerceAtMost(holder.viewWidth),
                                preview.height.coerceAtMost(holder.viewHeight)
                            ),
                            holder.viewWidth,
                            holder.viewHeight,
                            false
                        )
                    }
                }.onFailure {
                    Logger.error("failed to create preview $fileType", it)
                }.also {
                    tempFile.delete()
                }.getOrNull()
            } ?: return@also

            if (coroutineContext.job.isCancelled) return@also
            Handler(holder.view.context.mainLooper).post {
                holder.view.background = RoundedBitmapDrawableFactory.create(
                    holder.view.context.resources,
                    previewBitmap
                ).also {
                    it.cornerRadius = holder.radius.toFloat()
                }
            }
        }
    }

    private fun updateViewHolder(download: PendingDownload, holder: ViewHolder) {
        holder.status.text = download.downloadStage.toString()
        holder.view.background = holder.view.context.getDrawable(R.drawable.download_manager_item_background)

        coroutineScope.launch {
            withTimeout(2000) {
                handlePreview(download, holder)
            }
        }

        val isSaved = download.downloadStage == DownloadStage.SAVED
        //if the download is in progress, the user can cancel it
        val canInteract = if (download.job != null) !download.downloadStage.isFinalStage || isSaved
        else isSaved

        holder.status.visibility = if (isSaved) View.GONE else View.VISIBLE

        with(holder.actionButton) {
            isEnabled = canInteract
            alpha = if (canInteract) 1f else 0.5f
            background = context.getDrawable(if (isSaved) R.drawable.action_button_success else R.drawable.action_button_cancel)
            setTextColor(context.getColor(if (isSaved) R.color.successColor else R.color.actionBarColor))
            text = if (isSaved)
                SharedContext.translation["button.open"]
            else
                SharedContext.translation["button.cancel"]
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pendingDownload = downloadList[position]

        pendingDownload.changeListener = { _, _ ->
            Handler(holder.view.context.mainLooper).post {
                updateViewHolder(pendingDownload, holder)
                notifyItemChanged(position)
            }
        }

        holder.bitmojiIcon.setImageResource(R.drawable.bitmoji_blank)

        pendingDownload.metadata.iconUrl?.let { url ->
            thread(start = true) {
                runCatching {
                    val iconBitmap = URL(url).openStream().use {
                        BitmapFactory.decodeStream(it)
                    }
                    Handler(holder.view.context.mainLooper).post {
                        holder.bitmojiIcon.setImageBitmap(iconBitmap)
                    }
                }
            }
        }

        holder.title.visibility = View.GONE
        holder.subtitle.visibility = View.GONE

        pendingDownload.metadata.mediaDisplayType?.let {
            holder.title.text = it
            holder.title.visibility = View.VISIBLE
        }

        pendingDownload.metadata.mediaDisplaySource?.let {
            holder.subtitle.text = it
            holder.subtitle.visibility = View.VISIBLE
        }

        holder.actionButton.setOnClickListener {
            if (pendingDownload.downloadStage != DownloadStage.SAVED) {
                pendingDownload.cancel()
                pendingDownload.downloadStage = DownloadStage.CANCELLED
                updateViewHolder(pendingDownload, holder)
                notifyItemChanged(position);
                return@setOnClickListener
            }

            pendingDownload.outputFile?.let {
                fun showFileNotFound() {
                    Toast.makeText(holder.view.context, SharedContext.translation["download_manager_activity.file_not_found_toast"], Toast.LENGTH_SHORT).show()
                }

                val uri = Uri.parse(it)
                val fileType = runCatching {
                    if (uri.scheme == "content") {
                        activity.contentResolver.openInputStream(uri)?.use { input ->
                            FileType.fromInputStream(input)
                        } ?: run {
                            showFileNotFound()
                            return@setOnClickListener
                        }
                    } else {
                        val file = File(it)
                        if (!file.exists()) {
                            showFileNotFound()
                            return@setOnClickListener
                        }
                        FileType.fromFile(file)
                    }
                }.onFailure { exception ->
                    Logger.error("Failed to open file", exception)
                }.getOrDefault(FileType.UNKNOWN)
                if (fileType == FileType.UNKNOWN) {
                    showFileNotFound()
                    return@setOnClickListener
                }

                val intent = Intent(Intent.ACTION_VIEW)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                intent.setDataAndType(uri, fileType.mimeType)
                holder.view.context.startActivity(intent)
            }
        }

        updateViewHolder(pendingDownload, holder)
    }
}