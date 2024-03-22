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
        val textColour by context.config.userInterface.customizeUi.textColour
        val drawablebackgroundColour by context.config.userInterface.customizeUi.drawablebackgroundColour

        val userinputbackgroundcolour = try { Color.parseColor(backgroundColour) 
        } catch (e: IllegalArgumentException){
            Color.parseColor(DEFAULT_BACKGROUND_COLOUR)
        }
        val userinputtextcolour = try { Color.parseColor(textColour)
        } catch (e: IllegalArgumentException){
            Color.parseColor(DEFAULT_TEXT_COLOUR)
        }
        val userinputdrawablebackgroundcolour = try { Color.parseColor(drawablebackgroundColour)
        } catch (e: IllegalArgumentException){
            Color.parseColor(DEFAULT_DRAWABLE_COLOUR)
        }
        
        private static final String DEFAULT_BACKGROUND_COLOUR = "#FFFFFF";
        private Static final String DEFAULT_TEXT_COLOUR = "#000000";
        private Static final String DEFAULT_DRAWABLE_COLOUR = "#FFFFFF";
        
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
                    ephemeralHook("getColor", userinputtextcolour.toInt())
                }
                getAttribute("sigColorBackgroundMain"),
                getAttribute("sigColorBackgroundSurface") -> {
                    ephemeralHook("getColor", userinputbackgroundcolour.toInt())
                }
                getAttribute("actionSheetBackgroundDrawable"),
                getAttribute("actionSheetRoundedBackgroundDrawable") -> {
                    ephemeralHook("getDrawable", ColorDrawable(userinputdrawablebackgroundcolour.toInt()))
                }
            }
        }
    }
}
