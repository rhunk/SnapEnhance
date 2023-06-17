package me.rhunk.snapenhance.ui.download

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.download.MediaDownloadReceiver
import me.rhunk.snapenhance.download.data.PendingDownload

class DownloadManagerActivity : Activity() {
    private val fetchedDownloadTasks = mutableListOf<PendingDownload>()

    private val preferences by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private fun updateNoDownloadText() {
        findViewById<View>(R.id.no_download_title).let {
            it.visibility = if (fetchedDownloadTasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("BatteryLife", "NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val downloadTaskManager = MediaDownloadReceiver.downloadTaskManager.also { it.init(this) }

        actionBar?.apply {
            title = "Download Manager"
            setBackgroundDrawable(ColorDrawable(getColor(R.color.actionBarColor)))
        }
        setContentView(R.layout.download_manager_activity)
        
        window.navigationBarColor = getColor(R.color.primaryBackground)
    
        fetchedDownloadTasks.addAll(downloadTaskManager.queryAllTasks().values)


        with(findViewById<RecyclerView>(R.id.download_list)) {
            adapter = DownloadListAdapter(fetchedDownloadTasks).apply {
                registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                        updateNoDownloadText()
                    }
                })
            }

            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@DownloadManagerActivity)

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val download = fetchedDownloadTasks[viewHolder.absoluteAdapterPosition]
                    return if (download.isJobActive()) {
                        0
                    } else {
                        super.getMovementFlags(recyclerView, viewHolder)
                    }
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                @SuppressLint("NotifyDataSetChanged")
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    fetchedDownloadTasks.removeAt(viewHolder.absoluteAdapterPosition).let {
                        downloadTaskManager.removeTask(it)
                    }
                    adapter?.notifyItemRemoved(viewHolder.absoluteAdapterPosition)
                }
            }).attachToRecyclerView(this)

            var isLoading = false

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    val layoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                    if (lastVisibleItemPosition == RecyclerView.NO_POSITION) {
                        return
                    }

                    if (lastVisibleItemPosition == fetchedDownloadTasks.size - 1 && !isLoading) {
                        isLoading = true

                        downloadTaskManager.queryTasks(fetchedDownloadTasks.last().id).forEach {
                            fetchedDownloadTasks.add(it.value)
                            adapter?.notifyItemInserted(fetchedDownloadTasks.size - 1)
                        }

                        isLoading = false
                    }
                }
            })

            with(this@DownloadManagerActivity.findViewById<Button>(R.id.remove_all_button)) {
                setOnClickListener {
                    val dialog = AlertDialog.Builder(this@DownloadManagerActivity)
                    dialog.setTitle(R.string.remove_all_title)
                    dialog.setMessage(R.string.remove_all_text)
                    dialog.setPositiveButton("Yes") { _, _ ->
                        downloadTaskManager.removeAllTasks()
                        fetchedDownloadTasks.removeIf {
                            if (it.isJobActive()) it.cancel()
                            true
                        }
                        adapter?.notifyDataSetChanged()
                        updateNoDownloadText()
                    }
                    dialog.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    dialog.show()
                }
            }
        }

        updateNoDownloadText()

        if (preferences.getBoolean("ask_battery_optimisations", true)) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                with(Intent()) {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                    startActivityForResult(this, 1)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            preferences.edit().putBoolean("ask_battery_optimisations", false).apply()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        fetchedDownloadTasks.clear()
        fetchedDownloadTasks.addAll(MediaDownloadReceiver.downloadTaskManager.queryAllTasks().values)

        with(findViewById<RecyclerView>(R.id.download_list)) {
            adapter?.notifyDataSetChanged()
        }
        updateNoDownloadText()
    }
}