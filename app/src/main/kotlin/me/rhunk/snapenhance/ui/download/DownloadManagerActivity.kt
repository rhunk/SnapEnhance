package me.rhunk.snapenhance.ui.download

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.bridge.wrapper.TranslationWrapper
import me.rhunk.snapenhance.download.data.PendingDownload

class DownloadManagerActivity : Activity() {
    lateinit var translation: TranslationWrapper

    private val backCallbacks = mutableListOf<() -> Unit>()
    private val fetchedDownloadTasks = mutableListOf<PendingDownload>()
    private var listFilter = MediaFilter.NONE

    private val preferences by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private fun updateNoDownloadText() {
        findViewById<TextView>(R.id.no_download_title).let {
            it.text = translation["no_downloads"]
            it.visibility = if (fetchedDownloadTasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateListContent() {
        fetchedDownloadTasks.clear()
        fetchedDownloadTasks.addAll(SharedContext.downloadTaskManager.queryAllTasks(filter = listFilter).values)

        with(findViewById<RecyclerView>(R.id.download_list)) {
            adapter?.notifyDataSetChanged()
            scrollToPosition(0)
        }
        updateNoDownloadText()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        backCallbacks.lastOrNull()?.let {
            it()
            backCallbacks.removeLast()
        } ?: super.onBackPressed()
    }

    fun registerBackCallback(callback: () -> Unit) {
        backCallbacks.add(callback)
    }

    @SuppressLint("BatteryLife", "NotifyDataSetChanged", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SharedContext.ensureInitialized(this)
        translation = SharedContext.translation.getCategory("download_manager_activity")
        
        setContentView(R.layout.download_manager_activity)
        
        findViewById<TextView>(R.id.title).text = resources.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME

        findViewById<ImageButton>(R.id.debug_settings_button).setOnClickListener {
            DebugSettingsLayoutInflater(this).inflate(findViewById(android.R.id.content))
        }
        
        with(findViewById<RecyclerView>(R.id.download_list)) {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@DownloadManagerActivity)

            adapter = DownloadListAdapter(this@DownloadManagerActivity, fetchedDownloadTasks).apply {
                registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                        updateNoDownloadText()
                    }
                })
            }

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
                        SharedContext.downloadTaskManager.removeTask(it)
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

                        SharedContext.downloadTaskManager.queryTasks(fetchedDownloadTasks.last().downloadId, filter = listFilter).forEach {
                            fetchedDownloadTasks.add(it.value)
                            adapter?.notifyItemInserted(fetchedDownloadTasks.size - 1)
                        }

                        isLoading = false
                    }
                }
            })
    
            arrayOf(
                Pair(R.id.all_category, MediaFilter.NONE),
                Pair(R.id.pending_category, MediaFilter.PENDING),
                Pair(R.id.snap_category, MediaFilter.CHAT_MEDIA),
                Pair(R.id.story_category, MediaFilter.STORY),
                Pair(R.id.spotlight_category, MediaFilter.SPOTLIGHT)
            ).let { categoryPairs ->
                categoryPairs.forEach { pair ->
                    this@DownloadManagerActivity.findViewById<TextView>(pair.first).apply {
                        text = translation["category.${resources.getResourceEntryName(pair.first)}"]
                    }.setOnClickListener { view ->
                        listFilter = pair.second
                        updateListContent()
                        categoryPairs.map { this@DownloadManagerActivity.findViewById<TextView>(it.first) }.forEach {
                            it.setTextColor(getColor(R.color.primaryText))
                        }
                        (view as TextView).setTextColor(getColor(R.color.focusedCategoryColor))
                    }
                }
            }

            this@DownloadManagerActivity.findViewById<Button>(R.id.remove_all_button).also {
                it.text = translation["remove_all"]
            }.setOnClickListener {
                with(AlertDialog.Builder(this@DownloadManagerActivity)) {
                    setTitle(translation["remove_all_title"])
                    setMessage(translation["remove_all_text"])
                    setPositiveButton(SharedContext.translation["button.positive"]) { _, _ ->
                        SharedContext.downloadTaskManager.removeAllTasks()
                        fetchedDownloadTasks.removeIf {
                            if (it.isJobActive()) it.cancel()
                            true
                        }
                        adapter?.notifyDataSetChanged()
                        updateNoDownloadText()
                    }
                    setNegativeButton(SharedContext.translation["button.negative"]) { dialog, _ ->
                        dialog.dismiss()
                    }
                    show()
                }
            }

        }

        updateListContent()

        if (!preferences.getBoolean("ask_battery_optimisations", true) ||
            !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) return

        with(Intent()) {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
            startActivityForResult(this, 1)
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
        updateListContent()
    }
}