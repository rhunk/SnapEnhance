package me.rhunk.snapenhance.features.impl.ui.menus

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import de.robv.android.xposed.XposedBridge
import me.rhunk.snapenhance.Constants
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
import java.lang.reflect.Modifier

@SuppressLint("DiscouragedApi")
class MenuViewInjector : Feature("MenuViewInjector", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val friendFeedInfoMenu = FriendFeedInfoMenu()
    private val operaContextActionMenu = OperaContextActionMenu()
    private val chatActionMenu = ChatActionMenu()
    private val settingMenu = SettingsMenu()

    private val newChatString by lazy {
        context.resources.getString(context.resources.getIdentifier("new_chat", "string", Constants.SNAPCHAT_PACKAGE_NAME))
    }

    @SuppressLint("ResourceType")
    override fun asyncOnActivityCreate() {
        friendFeedInfoMenu.context = context
        operaContextActionMenu.context = context
        chatActionMenu.context = context
        settingMenu.context = context

        val actionSheetItemsContainerLayoutId = context.resources.getIdentifier("action_sheet_items_container", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val addViewMethod = ViewGroup::class.java.getMethod(
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType,
            ViewGroup.LayoutParams::class.java
        )

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
                if (viewGroup.parent == null || viewGroup.parent.parent == null) return@hook
                chatActionMenu.inject(viewGroup)
                return@hook
            }

            //TODO : preview group chats
            if (viewGroup is LinearLayout && viewGroup.id == actionSheetItemsContainerLayoutId) {
                val itemStringInterface by lazy {
                    childView.javaClass.declaredFields.filter {
                        !it.type.isPrimitive && Modifier.isAbstract(it.type.modifiers)
                    }.map {
                        runCatching {
                            it.isAccessible = true
                            it[childView]
                        }.getOrNull()
                    }.firstOrNull()
                }

                //the 3 dot button shows a menu which contains the first item as a Plain object
                if (viewGroup.getChildCount() == 0 && itemStringInterface != null && itemStringInterface.toString().startsWith("Plain(primaryText=$newChatString")) {

                    settingMenu.inject(viewGroup, originalAddView)
                    viewGroup.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}
                        override fun onViewDetachedFromWindow(v: View) {
                            context.config.writeConfig()
                        }
                    })
                    return@hook
                }
                if (context.feature(Messaging::class).lastFetchConversationUserUUID == null) return@hook

                //filter by the slot index
                if (viewGroup.getChildCount() != context.config.int(ConfigProperty.FRIEND_FEED_MENU_POSITION)) return@hook
                friendFeedInfoMenu.inject(viewGroup, originalAddView)
            }

        }
    }

}