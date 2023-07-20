package me.rhunk.snapenhance.ui.spoof

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.config.ConfigCategory
import me.rhunk.snapenhance.config.impl.ConfigIntegerValue
import me.rhunk.snapenhance.config.impl.ConfigStateValue
import me.rhunk.snapenhance.config.impl.ConfigStringValue
import me.rhunk.snapenhance.ui.ItemHelper

class DeviceSpooferActivity: Activity() {
    private val itemHelper = ItemHelper(this)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SharedContext.ensureInitialized(this)
        setContentView(R.layout.device_spoofer_activity)
        
        findViewById<TextView>(R.id.title).text = "Device Spoofer"
        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }
        val propertyListLayout = findViewById<ViewGroup>(R.id.spoof_property_list)
        
        SharedContext.config.entries().filter { it.key.category == ConfigCategory.DEVICE_SPOOFER }.forEach { (property, value) ->
            val configItem = layoutInflater.inflate(R.layout.config_activity_item, propertyListLayout, false)
            
            val propertyName = SharedContext.translation["property.${property.translationKey}.name"]
            
            fun addSeparator() {
                //add separator
                propertyListLayout.addView(View(this).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(getColor(R.color.tertiaryBackground))
                })
            }
            
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
            }
        
            propertyListLayout.addView(configItem)
            addSeparator()
        }
    }
    
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}