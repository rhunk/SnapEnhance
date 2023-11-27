package me.rhunk.snapenhance.core.ui.menu.impl

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.ui.ViewTagState
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.util.ktx.getIdentifier
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

@SuppressLint("DiscouragedApi")
class MenuViewInjector : Feature("MenuViewInjector", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val viewTagState = ViewTagState()

    private val menuMap = mutableMapOf<KClass<*>, AbstractMenu>()
    private val newChatString by lazy {
        context.resources.getString(context.resources.getIdentifier("new_chat", "string"))
    }

    @SuppressLint("ResourceType")
    override fun asyncOnActivityCreate() {
        menuMap[OperaContextActionMenu::class] = OperaContextActionMenu()
        menuMap[OperaDownloadIconMenu::class] = OperaDownloadIconMenu()
        menuMap[SettingsGearInjector::class] = SettingsGearInjector()
        menuMap[FriendFeedInfoMenu::class] = FriendFeedInfoMenu()
        menuMap[ChatActionMenu::class] = ChatActionMenu()
        menuMap[SettingsMenu::class] = SettingsMenu()

        menuMap.values.forEach { it.context = context; it.init() }

        val messaging = context.feature(Messaging::class)

        val actionSheetItemsContainerLayoutId = context.resources.getIdentifier("action_sheet_items_container", "id")
        val actionSheetContainer = context.resources.getIdentifier("action_sheet_container", "id")
        val actionMenu = context.resources.getIdentifier("action_menu", "id")
        val componentsHolder = context.resources.getIdentifier("components_holder", "id")
        val feedNewChat = context.resources.getIdentifier("feed_new_chat", "id")
        val contextMenuButtonIconView = context.resources.getIdentifier("context_menu_button_icon_view", "id")

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
            menuMap[OperaContextActionMenu::class]!!.inject(viewGroup, childView, originalAddView)

            if (childView.id == contextMenuButtonIconView) {
                menuMap[OperaDownloadIconMenu::class]!!.inject(viewGroup, childView, originalAddView)
            }

            if (event.parent.id == componentsHolder && childView.id == feedNewChat) {
                menuMap[SettingsGearInjector::class]!!.inject(viewGroup, childView, originalAddView)
                return@subscribe
            }

            //download in chat snaps and notes from the chat action menu
            if (viewGroup.javaClass.name.endsWith("ActionMenuChatItemContainer")) {
                if (viewGroup.parent == null || viewGroup.parent.parent == null) return@subscribe
                menuMap[ChatActionMenu::class]!!.inject(viewGroup, childView, originalAddView)
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
                    menuMap[FriendFeedInfoMenu::class]?.inject(event.parent, injectedLayout) { view ->
                        view.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 5, 0, 5)
                        }
                        viewList.add(view)
                    }

                    viewList.add(View(injectedLayout.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            30
                        )
                    })

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
                    menuMap[SettingsMenu::class]!!.inject(viewGroup, childView, originalAddView)
                    viewGroup.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}
                        override fun onViewDetachedFromWindow(v: View) {
                            viewTagState.removeState(viewGroup)
                        }
                    })
                    viewTagState[viewGroup]
                    return@subscribe
                }
                if (messaging.lastFetchConversationUUID == null || messaging.lastFetchConversationUserUUID == null) return@subscribe

                //filter by the slot index
                if (viewGroup.getChildCount() != context.config.userInterface.friendFeedMenuPosition.get()) return@subscribe
                if (viewTagState[viewGroup]) return@subscribe
                menuMap[FriendFeedInfoMenu::class]!!.inject(viewGroup, childView, originalAddView)
            }
        }
    }
}