package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.common.data.FriendAddSource
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
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
        }

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri != "/snapchat.friending.server.FriendAction/AddFriends") return@subscribe
            val spoofedSource = context.config.experimental.addFriendSourceSpoof.getNullable() ?: return@subscribe
            event.buffer = ProtoEditor(event.buffer).apply {
                edit {
                    fun setPage(value: String) {
                        remove(1)
                        addString(1, value)
                    }

                    editEach(2) {
                        remove(3) // remove suggestion token
                        fun setSource(source: FriendAddSource) {
                            remove(2)
                            addVarInt(2, source.id)
                        }

                        when (spoofedSource) {
                            "added_by_group_chat" -> {
                                setPage("group_profile")
                                setSource(FriendAddSource.GROUP_CHAT)
                            }
                            "added_by_username" -> {
                                setPage("search")
                                setSource(FriendAddSource.USERNAME)
                            }
                            "added_by_qr_code" -> {
                                setPage("scan_snapcode")
                                setSource(FriendAddSource.QR_CODE)
                            }
                            "added_by_mention" -> {
                                setPage("context_card")
                                setSource(FriendAddSource.MENTION)
                            }
                            "added_by_community" -> {
                                setPage("profile")
                                setSource(FriendAddSource.COMMUNITY)
                            }
                        }
                    }
                }
            }.toByteArray()
        }
    }
}