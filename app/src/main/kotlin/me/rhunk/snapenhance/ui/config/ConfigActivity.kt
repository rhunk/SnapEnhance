package me.rhunk.snapenhance.ui.config

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Html
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.bridge.wrapper.ConfigWrapper
import me.rhunk.snapenhance.config.ConfigCategory
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.ui.ItemHelper
import kotlin.math.abs
import kotlin.random.Random

typealias ActivityResultCallback = (requestCode: Int, resultCode: Int, data: Intent?) -> Unit

class ConfigActivity : Activity() {
    private val config = ConfigWrapper()
    private val itemHelper = ItemHelper(this)
    private val activityResultCallbacks = mutableMapOf<Int, ActivityResultCallback>()

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        config.writeConfig()
    }

    override fun onPause() {
        super.onPause()
        config.writeConfig()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        activityResultCallbacks[requestCode]?.invoke(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config.loadFromContext(this)
        SharedContext.ensureInitialized(this)
        setContentView(R.layout.config_activity)

        findViewById<View>(R.id.title_bar).let { titleBar ->
            titleBar.findViewById<TextView>(R.id.title).text = SharedContext.translation["config_activity.title"]
            titleBar.findViewById<ImageButton>(R.id.back_button).visibility = View.GONE
        }

        val propertyListLayout = findViewById<ViewGroup>(R.id.property_list)

        if (intent.getBooleanExtra("lspatched", false) ||
            applicationInfo.packageName != "me.rhunk.snapenhance" ||
            BuildConfig.DEBUG) {
            propertyListLayout.addView(
                layoutInflater.inflate(
                    R.layout.config_activity_debug_item,
                    propertyListLayout,
                    false
                ).apply {
                    findViewById<TextView>(R.id.debug_item_content).apply {
                        text = Html.fromHtml(
                            "You are using a <u><b>debug/unofficial</b></u> build!\n" +
                                    "Please consider downloading stable builds from <a href=\"https://github.com/rhunk/SnapEnhance\">GitHub</a>.",
                            Html.FROM_HTML_MODE_COMPACT
                        )
                        movementMethod = android.text.method.LinkMovementMethod.getInstance()
                    }
                })
        }

        var currentCategory: ConfigCategory? = null

        config.entries().filter { !it.key.category.hidden }.forEach { (property, value) ->
            val configItem = layoutInflater.inflate(R.layout.config_activity_item, propertyListLayout, false)

            fun addSeparator() {
                //add separator
                propertyListLayout.addView(View(this).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(getColor(R.color.tertiaryBackground))
                })
            }

            if (property.category != currentCategory) {
                if(!property.shouldAppearInSettings) return@forEach
                currentCategory = property.category
                with(layoutInflater.inflate(R.layout.config_activity_item, propertyListLayout, false)) {
                    findViewById<TextView>(R.id.name).apply {
                        text = SharedContext.translation["category.${property.category.key}"]
                        textSize = 20f
                        typeface = typeface?.let { android.graphics.Typeface.create(it, android.graphics.Typeface.BOLD) }
                    }
                    propertyListLayout.addView(this)
                }
                addSeparator()
            }

            if (!property.shouldAppearInSettings) return@forEach

            val propertyName = SharedContext.translation["property.${property.translationKey}.name"]

            configItem.findViewById<TextView>(R.id.name).text = propertyName
            configItem.findViewById<TextView>(R.id.description).also {
                it.text = SharedContext.translation["property.${property.translationKey}.description"]
                it.visibility = if (it.text.isEmpty()) View.GONE else View.VISIBLE
            }

            fun addValueView(view: View) {
                configItem.findViewById<ViewGroup>(R.id.value).addView(view)
            }

            when (value) {
                is ConfigStateValue -> {
                    val switch = Switch(this)
                    switch.isChecked = value.value()
                    switch.trackTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked)
                        ),
                        intArrayOf(
                            switch.highlightColor,
                            getColor(R.color.tertiaryBackground)
                        )
                    )
                    switch.setOnCheckedChangeListener { _, isChecked ->
                        value.writeFrom(isChecked.toString())
                    }
                    configItem.setOnClickListener { switch.toggle() }
                    addValueView(switch)
                }
                is ConfigStringValue, is ConfigIntegerValue -> {
                    val textView = itemHelper.createTranslatedTextView(property, shouldTranslatePropertyValue = false).also {
                        it.text = value.value().toString()
                    }
                    configItem.setOnClickListener {
                        if (value is ConfigStringValue && value.isFolderPath) {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                            val requestCode = abs(Random.nextInt())
                            activityResultCallbacks[requestCode] = let@{ _, resultCode, data ->
                                if (resultCode != RESULT_OK) return@let
                                val uri = data?.data ?: return@let
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                value.writeFrom(uri.toString())
                                textView.text = value.value()
                            }

                            startActivityForResult(intent, requestCode)
                            return@setOnClickListener
                        }

                        if (value is ConfigIntegerValue) {
                            itemHelper.askForValue(property, InputType.TYPE_CLASS_NUMBER) {
                                try {
                                    value.writeFrom(it)
                                    textView.text = value.value().toString()
                                } catch (e: NumberFormatException) {
                                    itemHelper.longToast(SharedContext.translation["config_activity.invalid_number_toast"], this)
                                }
                            }
                            return@setOnClickListener
                        }
                        itemHelper.askForValue(property, InputType.TYPE_CLASS_TEXT) {
                            value.writeFrom(it)
                            textView.text = value.value().toString()
                        }
                    }
                    addValueView(textView)
                }
                is ConfigStateListValue -> {
                    val textView = itemHelper.createTranslatedTextView(property, shouldTranslatePropertyValue = false)
                    val values = value.value()

                    fun updateText() {
                        textView.text = SharedContext.translation.format("config_activity.selected_text", "count" to values.filter { it.value }.size.toString())
                    }

                    updateText()

                    configItem.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle(propertyName)
                            .setPositiveButton(itemHelper.positiveButtonText) { _, _ ->
                                updateText()
                            }
                            .setMultiChoiceItems(
                                values.keys.map {
                                    if (property.disableValueLocalization) it
                                    else SharedContext.translation[property.getOptionTranslationKey(it)]
                                }.toTypedArray(),
                                values.map { it.value }.toBooleanArray()
                            ) { _, which, isChecked ->
                                value.setKey(values.keys.elementAt(which), isChecked)
                            }
                            .show()
                    }

                    addValueView(textView)
                }
                is ConfigStateSelection -> {
                    val textView = itemHelper.createTranslatedTextView(property, shouldTranslatePropertyValue = true)
                    textView.text = value.value()

                    configItem.setOnClickListener {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle(propertyName)

                        builder.setSingleChoiceItems(
                            value.keys().toTypedArray().map {
                                if (property.disableValueLocalization) it
                                else SharedContext.translation[property.getOptionTranslationKey(it)]
                            }.toTypedArray(),
                            value.keys().indexOf(value.value())
                        ) { _, which ->
                            value.writeFrom(value.keys()[which])
                        }

                        builder.setPositiveButton(itemHelper.positiveButtonText) { _, _ ->
                            textView.text = value.value()
                        }

                        builder.show()
                    }
                    addValueView(textView)
                }
            }

            propertyListLayout.addView(configItem)
            addSeparator()
        }

        propertyListLayout.addView(layoutInflater.inflate(R.layout.config_activity_debug_item, propertyListLayout, false).apply {
            findViewById<TextView>(R.id.debug_item_content).apply {
                text = Html.fromHtml("Made by rhunk on <a href=\"https://github.com/rhunk/SnapEnhance\">GitHub</a>", Html.FROM_HTML_MODE_COMPACT)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
            }
        })
    }
}