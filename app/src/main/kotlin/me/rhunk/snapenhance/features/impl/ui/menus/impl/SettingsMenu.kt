package me.rhunk.snapenhance.features.impl.ui.menus.impl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateListValue
import me.rhunk.snapenhance.config.impl.ConfigStateSelection
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.features.impl.ui.menus.AbstractMenu
import me.rhunk.snapenhance.features.impl.ui.menus.ViewAppearanceHelper

class SettingsMenu : AbstractMenu() {
    private fun createCategoryTitle(viewModel: View, key: String): TextView {
        val categoryText = TextView(viewModel.context)
        categoryText.text = context.translation.get(key)
        ViewAppearanceHelper.applyTheme(viewModel, categoryText)
        categoryText.textSize = 20f
        categoryText.typeface = categoryText.typeface?.let { Typeface.create(it, Typeface.BOLD) }
        return categoryText
    }

    @SuppressLint("SetTextI18n")
    private fun createPropertyView(viewModel: View, property: ConfigProperty): View {
        val updateButtonText: (TextView, String) -> Unit = { textView, text ->
            textView.text = "${context.translation.get(property.nameKey)} $text"
        }
        val updateStateSelectionText: (TextView, String) -> Unit = { textView, text ->
            updateButtonText(textView, text.let { if (it.isEmpty()) "(empty)" else ": $it" })
        }

        val textEditor: ((String) -> Unit) -> Unit = { updateValue ->
            val builder = AlertDialog.Builder(viewModel.context)
            builder.setTitle(context.translation.get(property.nameKey))

            val input = EditText(viewModel.context)
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(property.valueContainer.value().toString())

            builder.setView(input)
            builder.setPositiveButton("OK") { _, _ ->
                updateValue(input.text.toString())
            }

            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            builder.show()
        }

        val resultView: View = when (property.valueContainer) {
            is ConfigStringValue -> {
                val textView = TextView(viewModel.context)
                updateButtonText(textView, property.valueContainer.value)
                ViewAppearanceHelper.applyTheme(viewModel, textView)
                textView.setOnClickListener {
                    textEditor { value ->
                        property.valueContainer.value = value
                        updateButtonText(textView, value)
                    }
                }
                textView
            }
            is ConfigIntegerValue -> {
                val button = Button(viewModel.context)
                updateButtonText(button, property.valueContainer.value.toString())
                button.setOnClickListener {
                    textEditor { value ->
                        runCatching {
                            property.valueContainer.value = value.toInt()
                            updateButtonText(button, value)
                        }.onFailure {
                            context.shortToast("Invalid value")
                        }
                    }
                }
                ViewAppearanceHelper.applyTheme(viewModel, button)
                button
            }
            is ConfigStateValue -> {
                val switch = Switch(viewModel.context)
                switch.text = context.translation.get(property.nameKey)
                switch.isChecked = property.valueContainer.value
                switch.setOnCheckedChangeListener { _, isChecked ->
                    property.valueContainer.value = isChecked
                }
                ViewAppearanceHelper.applyTheme(viewModel, switch)
                switch
            }
            is ConfigStateSelection -> {
                val button = Button(viewModel.context)
                updateStateSelectionText(button, property.valueContainer.value())

                button.setOnClickListener {_ ->
                    val builder = AlertDialog.Builder(viewModel.context)
                    builder.setTitle(context.translation.get(property.nameKey))

                    builder.setSingleChoiceItems(
                        property.valueContainer.keys().toTypedArray(),
                        property.valueContainer.keys().indexOf(property.valueContainer.value())
                    ) { _, which ->
                        property.valueContainer.value(property.valueContainer.keys()[which])
                    }

                    builder.setPositiveButton("OK") { _, _ ->
                        updateStateSelectionText(button, property.valueContainer.value())
                    }

                    builder.show()
                }
                ViewAppearanceHelper.applyTheme(viewModel, button)
                button
            }
            is ConfigStateListValue -> {
                val button = Button(viewModel.context)
                updateStateSelectionText(button, property.valueContainer.toString())

                button.setOnClickListener {_ ->
                    val builder = AlertDialog.Builder(viewModel.context)
                    builder.setTitle(context.translation.get(property.nameKey))

                    val sortedStates = property.valueContainer.states.toSortedMap()

                    builder.setMultiChoiceItems(
                        sortedStates.toSortedMap().map { context.translation.get("option." + property.nameKey + "." +it.key) }.toTypedArray(),
                        sortedStates.map { it.value }.toBooleanArray()
                    ) { _, which, isChecked ->
                        sortedStates.keys.toList()[which].let { key ->
                            property.valueContainer.states[key] = isChecked
                        }
                    }

                    builder.setPositiveButton("OK") { _, _ ->
                        updateStateSelectionText(button, property.valueContainer.toString())
                    }

                    builder.show()
                }
                ViewAppearanceHelper.applyTheme(viewModel, button)
                button
            }
            else -> {
                TextView(viewModel.context)
            }
        }
        return resultView
    }

    @SuppressLint("SetTextI18n")
    @Suppress("deprecation")
    fun inject(viewModel: View, addView: (View) -> Unit) {
        val packageInfo = viewModel.context.packageManager.getPackageInfo(Constants.SNAPCHAT_PACKAGE_NAME, 0)
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

        addView(createCategoryTitle(viewModel, "category.debugging"))

        context.actionManager.getActions().forEach {
            val button = Button(viewModel.context)
            button.text = context.translation.get(it.nameKey)
            button.setOnClickListener { _ ->
                it.run()
            }
            ViewAppearanceHelper.applyTheme(viewModel, button)
            addView(button)
        }
    }
}