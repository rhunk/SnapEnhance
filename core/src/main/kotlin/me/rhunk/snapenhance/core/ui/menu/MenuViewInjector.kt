package me.rhunk.snapenhance.core.ui.menu

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.ui.menu.impl.*
import me.rhunk.snapenhance.core.util.ktx.getIdentifier

@SuppressLint("DiscouragedApi")
class MenuViewInjector : Feature("MenuViewInjector", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    @SuppressLint("ResourceType")
    override fun asyncOnActivityCreate() {
        val menuMap = arrayOf(
            OperaContextActionMenu(),
            OperaDownloadIconMenu(),
            SettingsGearInjector(),
            FriendFeedInfoMenu(),
            ChatActionMenu(),
            SettingsMenu()
        ).associateBy {
            it.context = context
            it.init()
            it::class
        }

        val messaging = context.feature(Messaging::class)

        val actionSheetItemsContainerLayoutId = context.resources.getIdentifier("action_sheet_items_container", "id")
        val actionSheetContainer = context.resources.getIdentifier("action_sheet_container", "id")
        val actionMenuHeaderId = context.resources.getIdentifier("action_menu_header", "id")
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

            if (event.view.id == actionMenuHeaderId) {
                event.parent.post {
                    val actionSheetItemsContainer = event.parent.findViewById<ViewGroup>(actionSheetItemsContainerLayoutId) ?: return@post
                    val views = mutableListOf<View>()
                    menuMap[FriendFeedInfoMenu::class]?.inject(event.parent, actionSheetItemsContainer) {
                        views.add(it)
                    }
                    views.reversed().forEach { actionSheetItemsContainer.addView(it, 0) }
                }
            }

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

                event.parent.post {
                    injectedLayout.addView(ScrollView(injectedLayout.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            weight = 1f;
                            setMargins(0, 100, 0, 0)
                        }

                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            menuMap[FriendFeedInfoMenu::class]?.inject(event.parent, injectedLayout) { view ->
                                view.layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 5, 0, 5)
                                }
                                addView(view)
                            }
                        })
                    }, 0)
                }

                event.view = injectedLayout
            }
        }
    }
}