package me.rhunk.snapenhance.ui.download

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.download.DownloadStage
import me.rhunk.snapenhance.download.PendingDownload
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class DownloadListAdapter(
    private val downloadList: MutableList<PendingDownload>
): Adapter<DownloadListAdapter.ViewHolder>() {
    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val bitmojiIcon: ImageView = view.findViewById(R.id.bitmoji_icon)
        val title: TextView = view.findViewById(R.id.item_title)
        val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        val status: TextView = view.findViewById(R.id.item_status)
        val actionButton: Button = view.findViewById(R.id.item_action_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.download_manager_item, parent, false))
    }

    override fun getItemCount(): Int {
        return downloadList.size
    }

    @SuppressLint("SetTextI18n")
    private fun setDownloadStage(holder: ViewHolder, downloadStage: DownloadStage) {
        holder.status.text = downloadStage.toString()

        if (!downloadStage.isFinalStage) return
        val isSaved = downloadStage == DownloadStage.SAVED
        holder.status.visibility = if (isSaved) View.GONE else View.VISIBLE

        with(holder.actionButton) {
            isEnabled = isSaved
            alpha = if (isSaved) 1f else 0.5f
            background = context.getDrawable(if (isSaved) R.drawable.action_button_success else R.drawable.action_button_cancel)
            setTextColor(context.getColor(if (isSaved) R.color.successColor else R.color.actionBarColor))
            text = if (isSaved) "Open" else "Cancel"
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pendingDownload = downloadList[position]

        pendingDownload.changeListener = { _, newState ->
            Handler(holder.view.context.mainLooper).post {
                setDownloadStage(holder, newState)
                notifyItemChanged(position)
            }
        }

        holder.bitmojiIcon.visibility = View.GONE

        pendingDownload.iconUrl?.let { url ->
            thread(start = true) {
                runCatching {
                    val iconBitmap = URL(url).openStream().use {
                        BitmapFactory.decodeStream(it)
                    }
                    Handler(holder.view.context.mainLooper).post {
                        holder.bitmojiIcon.setImageBitmap(iconBitmap)
                        holder.bitmojiIcon.visibility = View.VISIBLE
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
                setDownloadStage(holder, DownloadStage.CANCELLED)
                notifyItemChanged(position);
                return@setOnClickListener
            }

            pendingDownload.outputFile?.let {
                val file = File(it)
                if (!file.exists()) {
                    Toast.makeText(holder.view.context, "File does not exist", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(it), FileType.fromFile(File(it)).mimeType)
                holder.view.context.startActivity(intent)
            }
        }

        setDownloadStage(holder, pendingDownload.downloadStage)
    }
}