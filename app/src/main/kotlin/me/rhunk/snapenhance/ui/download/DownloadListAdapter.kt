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
        if (downloadStage == DownloadStage.SAVED) {
            holder.status.visibility = View.GONE
            with(holder.actionButton) {
                isEnabled = true
                alpha = 1f
                background = context.getDrawable(R.drawable.action_button_success)
                setTextColor(context.getColor(R.color.successColor))
                text = "Open"
            }
            return
        }
        holder.actionButton.isEnabled = false
        holder.actionButton.alpha = 0.5f
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val download = downloadList[position]

        download.changeListener = { _, newState ->
            Handler(holder.view.context.mainLooper).post {
                setDownloadStage(holder, newState)
                notifyItemChanged(position)
            }
        }

        holder.bitmojiIcon.visibility = View.GONE

        download.iconUrl?.let { url ->
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

        download.mediaDisplayType?.let {
            holder.title.text = it
            holder.title.visibility = View.VISIBLE
        }

        download.mediaDisplaySource?.let {
            holder.subtitle.text = it
            holder.subtitle.visibility = View.VISIBLE
        }

        holder.actionButton.setOnClickListener {
            if (download.downloadStage == DownloadStage.SAVED) {
                download.outputFile?.let {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(it), FileType.fromFile(File(it)).mimeType)
                    holder.view.context.startActivity(intent)
                }
                return@setOnClickListener
            }
            download.cancel()
            download.downloadStage = DownloadStage.CANCELLED
            setDownloadStage(holder, DownloadStage.CANCELLED)
            notifyItemChanged(position);
        }

        setDownloadStage(holder, download.downloadStage)
    }
}