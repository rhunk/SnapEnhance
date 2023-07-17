package me.rhunk.snapenhance.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.config.ConfigProperty

class ItemHelper(
    private val context : Context
) {
    val positiveButtonText by lazy {
        SharedContext.translation["button.ok"]
    }
    
    val cancelButtonText by lazy {
        SharedContext.translation["button.cancel"]
    }
    
    fun longToast(message: String, context: Context) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun createTranslatedTextView(property: ConfigProperty, shouldTranslatePropertyValue: Boolean = true): TextView {
        return object: TextView(context) {
            override fun setText(text: CharSequence?, type: BufferType?) {
                val newText = text?.takeIf { it.isNotEmpty() }?.let {
                    if (!shouldTranslatePropertyValue || property.disableValueLocalization) it
                    else SharedContext.translation[property.getOptionTranslationKey(it.toString())]
                }?.let {
                    if (it.length > 20) {
                        it.substring(0, 20) + "..."
                    } else {
                        it
                    }
                } ?: ""
                super.setTextColor(context.getColor(R.color.tertiaryText))
                super.setText(newText, type)
            }
        }
    }
    
    fun askForValue(property: ConfigProperty, requestedInputType: Int, callback: (String) -> Unit) {
        val editText = EditText(context).apply {
            inputType = requestedInputType
            setText(property.valueContainer.value().toString())
        }
        AlertDialog.Builder(context)
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
}