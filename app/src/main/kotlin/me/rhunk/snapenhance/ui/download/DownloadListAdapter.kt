package me.rhunk.snapenhance.ui.download

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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.download.data.PendingDownload
import me.rhunk.snapenhance.download.enums.DownloadStage
import me.rhunk.snapenhance.util.snap.PreviewUtils
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class DownloadListAdapter(
    private val activity: DownloadManagerActivity,
    private val downloadList: MutableList<PendingDownload>
): Adapter<DownloadListAdapter.ViewHolder>() {
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

    @OptIn(DelicateCoroutinesApi::class)
    private fun handlePreview(download: PendingDownload, holder: ViewHolder) {
        download.outputFile?.let { File(it) }?.takeIf { it.exists() }?.let {
            GlobalScope.launch(Dispatchers.IO) {
                val previewBitmap = PreviewUtils.createPreviewFromFile(it)?.let { preview ->
                    val offsetY = (preview.height / 2 - holder.viewHeight / 2).coerceAtLeast(0)

                    Bitmap.createScaledBitmap(
                        Bitmap.createBitmap(preview, 0, offsetY,
                            preview.width.coerceAtMost(holder.viewWidth),
                            preview.height.coerceAtMost(holder.viewHeight)
                        ),
                        holder.viewWidth,
                        holder.viewHeight,
                        false
                    )
                }?: return@launch

                if (coroutineContext.job.isCancelled) return@launch
                Handler(holder.view.context.mainLooper).post {
                    holder.view.background = RoundedBitmapDrawableFactory.create(holder.view.context.resources, previewBitmap).also {
                        it.cornerRadius = holder.radius.toFloat()
                    }
                }
            }.also { job ->
                previewJobs[holder.hashCode()] = job
            }
        }
    }

    private fun updateViewHolder(download: PendingDownload, holder: ViewHolder) {
        holder.status.text = download.downloadStage.toString()
        holder.view.background = holder.view.context.getDrawable(R.drawable.download_manager_item_background)

        handlePreview(download, holder)

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

        pendingDownload.iconUrl?.let { url ->
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

        pendingDownload.mediaDisplayType?.let {
            holder.title.text = it
            holder.title.visibility = View.VISIBLE
        }

        pendingDownload.mediaDisplaySource?.let {
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
                val file = File(it)
                if (!file.exists()) {
                    Toast.makeText(holder.view.context, activity.translation["file_not_found_toast"], Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(it), FileType.fromFile(File(it)).mimeType)
                holder.view.context.startActivity(intent)
            }
        }

        updateViewHolder(pendingDownload, holder)
    }
}