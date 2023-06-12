package me.rhunk.snapenhance.ui.download

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.download.DownloadStage
import me.rhunk.snapenhance.download.PendingDownload
import java.io.File

class DownloadListAdapter(
    private val downloadList: MutableList<PendingDownload>
): Adapter<DownloadListAdapter.ViewHolder>() {
    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val itemName: TextView = view.findViewById(R.id.download_manager_item_name)
        val itemStatus: TextView = view.findViewById(R.id.download_manager_item_status)
        val itemCancel: Button = view.findViewById(R.id.download_manager_item_cancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.download_manager_item, parent, false))
    }

    override fun getItemCount(): Int {
        return downloadList.size
    }

    private fun setDownloadStage(holder: ViewHolder, downloadStage: DownloadStage) {
        holder.itemStatus.text = downloadStage.toString()
        if (!downloadStage.isFinalStage) return
        holder.itemCancel.isEnabled = false
        holder.itemCancel.alpha = 0.5f
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val download = downloadList[position]

        download.changeListener = { _, newState ->
            Handler(holder.view.context.mainLooper).post {
                setDownloadStage(holder, newState)
                notifyItemChanged(position)
            }
        }

        holder.view.setOnClickListener {
            if (download.downloadStage != DownloadStage.SAVED) return@setOnClickListener
            download.outputFile?.let {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(it), FileType.fromFile(File(it)).mimeType)
                holder.view.context.startActivity(intent)
            }
        }

        holder.itemName.text = (download.outputFile ?: download.outputPath).split("/").takeLast(2).joinToString("/").let {
            if (it.length > 30) it.take(30) + "..." else it
        }

        holder.itemCancel.setOnClickListener {
            download.cancel()
            download.downloadStage = DownloadStage.CANCELLED
            setDownloadStage(holder, DownloadStage.CANCELLED)
            notifyItemChanged(position);
        }

        setDownloadStage(holder, download.downloadStage)
    }
}