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
        val textColour by context.config.userInterface.customizeUi.textColour
        val effectiveTextColour = if(textColour.isEmpty()) {
            Color.parseColor("#ffffff")
        }else {
            try {
                Color.parseColor(textColour)
            }catch (e: IllegalArgumentException){
                Color.parseColor("#ffffff")
            }
        }
        
        val backgroundColour by context.config.userInterface.customizeUi.backgroundColour
        val effectiveBackgroundColour = if(backgroundColour.isEmpty()) {
            Color.parseColor("#000000")
        }else {
            try {
                Color.parseColor(backgroundColour)
            }catch (e: IllegalArgumentException){
                Color.parseColor("#000000")
            }
        }
        
        val backgroundColoursurface by context.config.userInterface.customizeUi.backgroundColoursurface
        val effectivebackgroundColoursurface = if(backgroundColoursurface.isEmpty()) {
            Color.parseColor("#000000")
        }else {
            try {
                Color.parseColor(backgroundColoursurface)
            }catch (e: IllegalArgumentException){
                Color.parseColor("#000000")
            }
        }
        
        val actionMenubackgroundColour by context.config.userInterface.customizeUi.actionMenubackgroundColour
        val effectiveactionMenubackgroundColour = if(actionMenubackgroundColour.isEmpty()) {
            Color.parseColor("#000000")
        }else {
            try {
                Color.parseColor(actionMenubackgroundColour)
            }catch (e: IllegalArgumentException){
                Color.parseColor("#000000")
            }
        }
        
        val actionMenuRoundbackgroundColour by context.config.userInterface.customizeUi.actionMenuRoundbackgroundColour
        val effectiveactionMenuRoundbackgroundColour = if(actionMenuRoundbackgroundColour.isEmpty()) {
            Color.parseColor("#000000")
        }else {
            try {
                Color.parseColor(actionMenuRoundbackgroundColour)
            }catch (e: IllegalArgumentException){
                Color.parseColor("#000000")
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
                
                getAttribute("sigColorBackgroundMain") -> {
                    ephemeralHook("getColor", effectiveBackgroundColour.toInt())
                }
                
                getAttribute("sigColorBackgroundSurface") -> {
                    ephemeralHook("getColor", effectivebackgroundColoursurface.toInt())
                }
                
                getAttribute("actionSheetBackgroundDrawable") -> {
                    ephemeralHook("getDrawable", ColorDrawable(effectiveactionMenubackgroundColour.toInt()))
                }
                
                getAttribute("actionSheetRoundedBackgroundDrawable") -> {
                    ephemeralHook("getDrawable", ColorDrawable(effectiveactionMenuRoundbackgroundColour.toInt()))
                }
            }
        }
    }
}

                
