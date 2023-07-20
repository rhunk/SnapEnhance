package me.rhunk.snapenhance.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.util.ActivityResultCallback
import kotlin.math.abs
import kotlin.random.Random

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
                        "...${it.substring(it.length - 20)}"
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

    fun askForFolder(activity: Activity, property: ConfigProperty, callback: (String) -> Unit): Pair<Int, ActivityResultCallback> {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val requestCode = abs(Random.nextInt())
        activity.startActivityForResult(intent, requestCode)

        return requestCode to let@{_, resultCode, data ->
            if (resultCode != Activity.RESULT_OK) return@let
            val uri = data?.data ?: return@let
            val value = uri.toString()
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            property.valueContainer.writeFrom(value)
            callback(value)
        }
    }
}
