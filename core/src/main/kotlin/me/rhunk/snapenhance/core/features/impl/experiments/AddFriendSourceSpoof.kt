package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.mapper.impl.FriendRelationshipChangerMapper

class AddFriendSourceSpoof : Feature("AddFriendSourceSpoof", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    var friendRelationshipChangerInstance: Any? = null
        private set

    override fun onActivityCreate() {
        context.mappings.useMapper(FriendRelationshipChangerMapper::class) {
            classReference.get()?.hookConstructor(HookStage.AFTER) { param ->
                friendRelationshipChangerInstance = param.thisObject()
            }

            classReference.get()?.hook(addFriendMethod.get()!!, HookStage.BEFORE) { param ->
                val spoofedSource = context.config.experimental.addFriendSourceSpoof.getNullable() ?: return@hook

                fun setEnum(index: Int, value: String) {
                    val enumData = param.arg<Any>(index)
                    enumData::class.java.enumConstants.first { it.toString() == value }.let {
                        param.setArg(index, it)
                    }
                }

                when (spoofedSource) {
                    "added_by_quick_add" -> {
                        setEnum(1, "PROFILE")
                        setEnum(2, "ADD_FRIENDS_BUTTON_ON_TOP_BAR_ON_FRIENDS_FEED")
                        setEnum(3, "ADDED_BY_SUGGESTED")
                    }
                    "added_by_group_chat" -> {
                        setEnum(1, "PROFILE")
                        setEnum(2, "GROUP_PROFILE")
                        setEnum(3, "ADDED_BY_GROUP_CHAT")
                    }
                    "added_by_username" -> {
                        setEnum(1, "SEARCH")
                        setEnum(2, "SEARCH")
                        setEnum(3, "ADDED_BY_USERNAME")
                    }
                    "added_by_qr_code" -> {
                        setEnum(1, "PROFILE")
                        setEnum(2, "PROFILE")
                        setEnum(3, "ADDED_BY_QR_CODE")
                    }
                    "added_by_mention" -> {
                        setEnum(1, "CONTEXT_CARDS")
                        setEnum(2, "CONTEXT_CARD")
                        setEnum(3, "ADDED_BY_MENTION")
                    }
                    "added_by_community" -> {
                        setEnum(1, "PROFILE")
                        setEnum(2, "PROFILE")
                        setEnum(3, "ADDED_BY_COMMUNITY")
                    }
                    else -> return@hook
                }
            }
        }
    }
}