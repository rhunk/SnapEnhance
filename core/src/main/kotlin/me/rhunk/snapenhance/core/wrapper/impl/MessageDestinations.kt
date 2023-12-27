package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.core.wrapper.AbstractWrapper

class MessageDestinations(obj: Any) : AbstractWrapper(obj){
    var conversations by field("mConversations", uuidArrayListMapper)
    var stories by field<ArrayList<*>>("mStories")
    var mPhoneNumbers by field<ArrayList<*>>("mPhoneNumbers")
}