package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.eventbus.events.impl.AddViewEvent
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import java.lang.reflect.Modifier

@SuppressLint("DiscouragedApi")
class MenuViewInjector : Feature("MenuViewInjector", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val friendFeedInfoMenu = FriendFeedInfoMenu()
    private val operaContextActionMenu = OperaContextActionMenu()
    private val chatActionMenu = ChatActionMenu()
    private val settingMenu = SettingsMenu()
    private val settingsGearInjector = SettingsGearInjector()

    private val newChatString by lazy {
        context.resources.getString(context.resources.getIdentifier("new_chat", "string", Constants.SNAPCHAT_PACKAGE_NAME))
    }

    @SuppressLint("ResourceType")
    override fun asyncOnActivityCreate() {
        friendFeedInfoMenu.context = context
        operaContextActionMenu.context = context
        chatActionMenu.context = context
        settingMenu.context = context
        settingsGearInjector.context = context

        val messaging = context.feature(Messaging::class)

        val actionSheetItemsContainerLayoutId = context.resources.getIdentifier("action_sheet_items_container", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val actionSheetContainer = context.resources.getIdentifier("action_sheet_container", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val actionMenu = context.resources.getIdentifier("action_menu", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val componentsHolder = context.resources.getIdentifier("components_holder", "id", Constants.SNAPCHAT_PACKAGE_NAME)
        val feedNewChat = context.resources.getIdentifier("feed_new_chat", "id", Constants.SNAPCHAT_PACKAGE_NAME)

        context.event.subscribe(AddViewEvent::class) { event ->
            val originalAddView: (View) -> Unit = {
                event.adapter.invokeOriginal(arrayOf(it, -1,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                )
            }

            val viewGroup: ViewGroup = event.parent
            val childView: View = event.view
            operaContextActionMenu.inject(event.parent, childView)

            if (event.parent.id == componentsHolder && childView.id == feedNewChat) {
                settingsGearInjector.inject(event.parent, childView)
                return@subscribe
            }

            //download in chat snaps and notes from the chat action menu
            if (viewGroup.javaClass.name.endsWith("ActionMenuChatItemContainer")) {
                if (viewGroup.parent == null || viewGroup.parent.parent == null) return@subscribe
                chatActionMenu.inject(viewGroup)
                return@subscribe
            }

            //TODO: inject in group chat menus
            if (viewGroup.id == actionSheetContainer && childView.id == actionMenu && messaging.lastFetchGroupConversationUUID != null) {
                val injectedLayout = LinearLayout(childView.context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.BOTTOM
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    addView(childView)
                    addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}
                        override fun onViewDetachedFromWindow(v: View) {
                            messaging.lastFetchGroupConversationUUID = null
                        }
                    })
                }

                val viewList = mutableListOf<View>()
                context.runOnUiThread {
                    friendFeedInfoMenu.inject(injectedLayout) { view ->
                        view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 3, 0, 3)
                        }
                        viewList.add(view)
                    }
                    viewList.reversed().forEach { injectedLayout.addView(it, 0) }
                }

                event.view = injectedLayout
            }

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
                            //context.config.writeConfig()
                        }
                    })
                    return@subscribe
                }
                if (messaging.lastFetchConversationUUID == null || messaging.lastFetchConversationUserUUID == null) return@subscribe

                //filter by the slot index
                if (viewGroup.getChildCount() != context.config.userInterface.friendFeedMenuPosition.get()) return@subscribe
                friendFeedInfoMenu.inject(viewGroup, originalAddView)
            }
        }
    }
}