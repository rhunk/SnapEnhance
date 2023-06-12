package me.rhunk.snapenhance.ui.download

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.download.MediaDownloadReceiver

class DownloadManagerActivity : Activity() {
    private val preferences by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private fun updateNoDownloadText() {
        findViewById<View>(R.id.no_download_title).let {
            it.visibility = if (MediaDownloadReceiver.downloadTasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.apply {
            title = "Download Manager"
            setBackgroundDrawable(ColorDrawable(getColor(R.color.actionBarColor)))
        }
        setContentView(R.layout.download_manager_activity)

        with(findViewById<RecyclerView>(R.id.download_list)) {
            adapter = DownloadListAdapter(MediaDownloadReceiver.downloadTasks).apply {
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
                    val download = MediaDownloadReceiver.downloadTasks[viewHolder.absoluteAdapterPosition]
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
                    MediaDownloadReceiver.downloadTasks.removeAt(viewHolder.absoluteAdapterPosition)
                    adapter?.notifyItemRemoved(viewHolder.absoluteAdapterPosition)
                }
            }).attachToRecyclerView(this)
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
        with(findViewById<RecyclerView>(R.id.download_list)) {
            adapter?.notifyDataSetChanged()
        }
        updateNoDownloadText()
    }
}