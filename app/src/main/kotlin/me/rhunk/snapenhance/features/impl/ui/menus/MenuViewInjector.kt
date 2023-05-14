package me.rhunk.snapenhance.features.impl.ui.menus

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import de.robv.android.xposed.XposedBridge
import me.rhunk.snapenhance.Constants.VIEW_DRAWER
import me.rhunk.snapenhance.Constants.VIEW_INJECTED_CODE
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.ui.menus.impl.ChatActionMenu
import me.rhunk.snapenhance.features.impl.ui.menus.impl.FriendFeedInfoMenu
import me.rhunk.snapenhance.features.impl.ui.menus.impl.OperaContextActionMenu
import me.rhunk.snapenhance.features.impl.ui.menus.impl.SettingsMenu
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class MenuViewInjector : Feature("MenuViewInjector", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val friendFeedInfoMenu = FriendFeedInfoMenu()
    private val operaContextActionMenu = OperaContextActionMenu()
    private val chatActionMenu = ChatActionMenu()
    private val settingMenu = SettingsMenu()

    private fun wasInjectedView(view: View): Boolean {
        if (view.getTag(VIEW_INJECTED_CODE) != null) return true
        view.setTag(VIEW_INJECTED_CODE, true)
        return false
    }

    @SuppressLint("ResourceType")
    override fun asyncOnActivityCreate() {
        friendFeedInfoMenu.context = context
        operaContextActionMenu.context = context
        chatActionMenu.context = context
        settingMenu.context = context

        val addViewMethod = ViewGroup::class.java.getMethod(
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType,
            ViewGroup.LayoutParams::class.java
        )

        //catch the card view instance in the action drawer
        Hooker.hook(
            LinearLayout::class.java.getConstructor(
                Context::class.java,
                AttributeSet::class.java,
                Int::class.javaPrimitiveType
            ), HookStage.AFTER
        ) { param ->
            val viewGroup: LinearLayout = param.thisObject()
            val attribute: Int = param.arg(2)
            if (attribute == 0) return@hook
            val resourceName = viewGroup.resources.getResourceName(attribute)
            if (!resourceName.endsWith("snapCardContentLayoutStyle")) return@hook
            viewGroup.setTag(VIEW_DRAWER, Any())
        }

        Hooker.hook(addViewMethod, HookStage.BEFORE) { param ->
            val viewGroup: ViewGroup = param.thisObject()
            val originalAddView: (View) -> Unit = { view: View ->
                XposedBridge.invokeOriginalMethod(
                    addViewMethod,
                    viewGroup,
                    arrayOf(
                        view,
                        -1,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                )
            }

            val childView: View = param.arg(0)
            operaContextActionMenu.inject(viewGroup, childView)

            //download in chat snaps and notes from the chat action menu
            if (viewGroup.javaClass.name.endsWith("ActionMenuChatItemContainer")) {
                if (viewGroup.parent == null || viewGroup.parent
                        .parent == null
                ) return@hook
                chatActionMenu.inject(viewGroup)
                return@hook
            }

            //TODO : preview group chats
            if (viewGroup !is LinearLayout) return@hook
            if (viewGroup.getTag(VIEW_DRAWER) == null) return@hook
            val itemStringInterface =childView.javaClass.declaredFields.filter { field: Field ->
                        !field.type.isPrimitive && Modifier.isAbstract(
                            field.type.modifiers
                        )
                    }
                    .map { field: Field ->
                        try {
                            field.isAccessible = true
                            return@map field[childView]
                        } catch (e: IllegalAccessException) {
                            e.printStackTrace()
                        }
                        null
                    }.firstOrNull()

            //the 3 dot button shows a menu which contains the first item as a Plain object
            //FIXME: better way to detect the 3 dot button
           if (viewGroup.getChildCount() == 0 && itemStringInterface != null && itemStringInterface.toString().startsWith("Plain(primaryText=")) {
                if (wasInjectedView(viewGroup)) return@hook

                settingMenu.inject(viewGroup, originalAddView)
                viewGroup.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View?) {}
                    override fun onViewDetachedFromWindow(v: View?) {
                        context.config.writeConfig()
                    }
                })
                return@hook
            }
            if (context.feature(Messaging::class).lastFetchConversationUserUUID == null) return@hook

            //filter by the slot index
            if (viewGroup.getChildCount() != context.config.int(ConfigProperty.MENU_SLOT_ID)) return@hook

            friendFeedInfoMenu.inject(viewGroup, originalAddView)
            childView.setTag(VIEW_DRAWER, null)
        }
    }

}