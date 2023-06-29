package me.rhunk.snapenhance.ui.config

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Html
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.bridge.ConfigWrapper
import me.rhunk.snapenhance.config.ConfigCategory
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue

class ConfigActivity : Activity() {
    private val config = ConfigWrapper()

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

    private val positiveButtonText by lazy {
        SharedContext.translation["button.ok"]
    }

    private val cancelButtonText by lazy {
        SharedContext.translation["button.cancel"]
    }

    private fun longToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun createTranslatedTextView(property: ConfigProperty, shouldTranslatePropertyValue: Boolean = true): TextView {
        return object: TextView(this) {
            override fun setText(text: CharSequence?, type: BufferType?) {
                val newText = text?.takeIf { it.isNotEmpty() }?.let {
                    if (!shouldTranslatePropertyValue || property.disableValueLocalization) it
                    else SharedContext.translation["option.property." + property.translationKey + "." + it]
                }?.let {
                    if (it.length > 20) {
                        it.substring(0, 20) + "..."
                    } else {
                        it
                    }
                } ?: ""
                super.setTextColor(getColor(R.color.tertiaryText))
                super.setText(newText, type)
            }
        }
    }

    private fun askForValue(property: ConfigProperty, requestedInputType: Int, callback: (String) -> Unit) {
        val editText = EditText(this).apply {
            inputType = requestedInputType
            setText(property.valueContainer.value().toString())
        }
        AlertDialog.Builder(this)
            .setTitle(SharedContext.translation["property.${property.translationKey}.name"])
            .setView(editText)
            .setPositiveButton(positiveButtonText) { _, _ ->
                callback(editText.text.toString())
            }
            .setNegativeButton(cancelButtonText) { dialog, _ ->
                dialog.cancel()
            }
            .show()
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

        config.entries().forEach { (property, value) ->
            val configItem = layoutInflater.inflate(R.layout.config_activity_item, propertyListLayout, false)

            fun addSeparator() {
                //add separator
                propertyListLayout.addView(View(this).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(getColor(R.color.tertiaryBackground))
                })
            }

            if (property.category != currentCategory) {
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
                    val textView = createTranslatedTextView(property, shouldTranslatePropertyValue = false).also {
                        it.text = value.value().toString()
                    }
                    configItem.setOnClickListener {
                        if (value is ConfigIntegerValue) {
                            askForValue(property, InputType.TYPE_CLASS_NUMBER) {
                                try {
                                    value.writeFrom(it)
                                    textView.text = value.value().toString()
                                } catch (e: NumberFormatException) {
                                    longToast(SharedContext.translation["config_activity.invalid_number_toast"])
                                }
                            }
                            return@setOnClickListener
                        }
                        askForValue(property, InputType.TYPE_CLASS_TEXT) {
                            value.writeFrom(it)
                            textView.text = value.value().toString()
                        }
                    }
                    addValueView(textView)
                }
                is ConfigStateListValue -> {
                    val textView = createTranslatedTextView(property, shouldTranslatePropertyValue = false)
                    val values = value.value()

                    fun updateText() {
                        textView.text = SharedContext.translation.format("config_activity.selected_text", "count" to values.filter { it.value }.size.toString())
                    }

                    updateText()

                    configItem.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle(propertyName)
                            .setPositiveButton(positiveButtonText) { _, _ ->
                                updateText()
                            }
                            .setMultiChoiceItems(
                                values.keys.map {
                                    if (property.disableValueLocalization) it
                                    else SharedContext.translation["option.property." + property.translationKey + "." + it]
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
                    val textView = createTranslatedTextView(property, shouldTranslatePropertyValue = true)
                    textView.text = value.value()

                    configItem.setOnClickListener {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle(propertyName)

                        builder.setSingleChoiceItems(
                            value.keys().toTypedArray().map {
                                if (property.disableValueLocalization) it
                                else SharedContext.translation["option.property." + property.translationKey + "." + it]
                            }.toTypedArray(),
                            value.keys().indexOf(value.value())
                        ) { _, which ->
                            value.writeFrom(value.keys()[which])
                        }

                        builder.setPositiveButton(positiveButtonText) { _, _ ->
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