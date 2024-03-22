package me.rhunk.snapenhance.core.features.impl.experiments

import android.annotation.SuppressLint
import android.content.res.TypedArray
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.Hooker
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getIdentifier

class CustomizeUi: Feature("Customize_Ui", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    @SuppressLint("DiscouragedApi")
    override fun onActivityCreate() {
        if (context.config.userInterface.customizeUi.globalState != true) return
        
        val backgroundColour by context.config.userInterface.customizeUi.backgroundColour
        val effectiveBackgroundColour = if(backgroundColour.isEmpty()) {
            Color.parseColor(#ff0000)
        }else {
            try {
                Color.parseColor(backgroundColour)
            }catch (e: IllegalArgumentException){
                Color.parseColor(#1803ff)
            }
        }
        
        val textColour by context.config.userInterface.customizeUi.textColour
        val effectiveTextColour = if(textcolour.isEmpty()) {
            Color.parseColor(#2bff00)
        }else {
            try {
                Color.parseColor(textColour)
            }catch (e: IllegalArgumentException){
                Color.parseColor(#ffa200)
            }
        }
        
        val drawablebackgroundColour by context.config.userInterface.customizeUi.drawablebackgroundColour
        val effectiveDrawableBackgroundColour = if(drawablebackgroundColour.isEmpty()) {
            Color.parseColor(#ae00ff)
        }else {
            try {
                Color.parseColor(drawablebackgroundColour)
            }catch (e: IllegalArgumentException){
                Color.parseColor(#ff0090)
            }
        }

        val attributeCache = mutableMapOf<String, Int>()

        fun getAttribute(name: String): Int {
            if (attributeCache.containsKey(name)) return attributeCache[name]!!
            return context.resources.getIdentifier(name, "attr").also { attributeCache[name] = it }
        }

        context.androidContext.theme.javaClass.getMethod("obtainStyledAttributes", IntArray::class.java).hook(
            HookStage.AFTER) { param ->
            val array = param.arg<IntArray>(0)
            val result = param.getResult() as TypedArray

            fun ephemeralHook(methodName: String, content: Any) {
                Hooker.ephemeralHookObjectMethod(result::class.java, result, methodName, HookStage.BEFORE) {
                    it.setResult(content)
                }
            }

            when (array[0]) {
                getAttribute("sigColorTextPrimary") -> {
                    ephemeralHook("getColor", effectiveTextColour.toInt())
                }
                getAttribute("sigColorBackgroundMain"),
                getAttribute("sigColorBackgroundSurface") -> {
                    ephemeralHook("getColor", effectiveBackgroundColour.toInt())
                }
                getAttribute("actionSheetBackgroundDrawable"),
                getAttribute("actionSheetRoundedBackgroundDrawable") -> {
                    ephemeralHook("getDrawable", ColorDrawable(effectiveDrawableBackgroundColour.toInt()))
                }
            }
        }
    }
}

                
