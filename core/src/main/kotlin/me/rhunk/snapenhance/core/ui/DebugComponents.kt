package me.rhunk.snapenhance.core.ui

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ScrollView

fun debugEditText(context: Context, initialText: String): View {
    return ScrollView(context).apply {
        isSmoothScrollingEnabled = true
        addView(EditText(context).apply {
            inputType = InputType.TYPE_NULL
            isSingleLine = false
            setTextColor(resources.getColor(android.R.color.white, context.theme))
            setTextIsSelectable(true)
            textSize = 12f
            setPadding(20, 20, 20, 20)
            setText(initialText)
        })
    }
}