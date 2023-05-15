package me.rhunk.snapenhance.features.impl.ui.menus.impl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.impl.ui.menus.AbstractMenu
import me.rhunk.snapenhance.features.impl.ui.menus.ViewAppearanceHelper

class SettingsMenu : AbstractMenu() {
    private fun createCategoryTitle(viewModel: View, key: String): TextView {
        val categoryText = TextView(viewModel.context)
        categoryText.text = context.translation.get(key)
        ViewAppearanceHelper.applyTheme(viewModel, categoryText)
        categoryText.textSize = 18f
        return categoryText
    }

    @SuppressLint("SetTextI18n")
    private fun createPropertyView(viewModel: View, property: ConfigProperty): View {
        val updateButtonText: (TextView, String) -> Unit = { textView, text ->
            textView.text = "${context.translation.get(property.nameKey)} $text"
        }

        val textEditor: ((String) -> Unit) -> Unit = { updateValue ->
            val builder = AlertDialog.Builder(viewModel.context)
            builder.setTitle(context.translation.get(property.nameKey))

            val input = EditText(viewModel.context)
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(context.config.get(property).toString())

            builder.setView(input)
            builder.setPositiveButton("OK") { _, _ ->
                updateValue(input.text.toString())
            }

            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            builder.show()
        }

        val resultView: View = when (property.defaultValue) {
            is String -> {
                val textView = TextView(viewModel.context)
                updateButtonText(textView, context.config.string(property))
                ViewAppearanceHelper.applyTheme(viewModel, textView)
                textView.setOnClickListener {
                    textEditor { value ->
                        context.config.set(property, value)
                        updateButtonText(textView, value)
                    }
                }
                textView
            }
            is Number -> {
                val button = Button(viewModel.context)
                updateButtonText(button, context.config.get(property).toString())
                button.setOnClickListener {
                    textEditor { value ->
                        runCatching {
                            context.config.set(property, when (property.defaultValue) {
                                is Int -> value.toInt()
                                is Double -> value.toDouble()
                                is Float -> value.toFloat()
                                is Long -> value.toLong()
                                is Short -> value.toShort()
                                is Byte -> value.toByte()
                                else -> throw IllegalArgumentException()
                            })
                            updateButtonText(button, value)
                        }.onFailure {
                            context.shortToast("Invalid value")
                        }
                    }
                }
                ViewAppearanceHelper.applyTheme(viewModel, button)
                button
            }
            is Boolean -> {
                val switch = Switch(viewModel.context)
                switch.text = context.translation.get(property.nameKey)
                switch.isChecked = context.config.bool(property)
                switch.setOnCheckedChangeListener { _, isChecked ->
                    context.config.set(property, isChecked)
                }
                ViewAppearanceHelper.applyTheme(viewModel, switch)
                switch
            }
            else -> {
                TextView(viewModel.context)
            }
        }
        return resultView
    }

    @SuppressLint("SetTextI18n")
    fun inject(viewModel: View, addView: (View) -> Unit) {
        val packageInfo = viewModel.context.packageManager.getPackageInfo(
            Constants.SNAPCHAT_PACKAGE_NAME,
            0
        )
        val versionTextBuilder = StringBuilder()
        versionTextBuilder.append("SnapEnhance ").append(BuildConfig.VERSION_NAME)
            .append(" by rhunk")
        if (BuildConfig.DEBUG) {
            versionTextBuilder.append("\n").append("Snapchat ").append(packageInfo.versionName)
                .append(" (").append(packageInfo.longVersionCode).append(")")
        }
        val titleText = TextView(viewModel.context)
        titleText.text = versionTextBuilder.toString()
        ViewAppearanceHelper.applyTheme(viewModel, titleText)
        titleText.textSize = 18f
        titleText.minHeight = 80 * versionTextBuilder.chars().filter { ch: Int -> ch == '\n'.code }
                .count().coerceAtLeast(2).toInt()
        addView(titleText)

        context.config.entries().groupBy {
            it.key.category
        }.forEach { (category, value) ->
            addView(createCategoryTitle(viewModel, category.key))
            value.forEach {
                addView(createPropertyView(viewModel, it.key))
            }
        }
    }
}