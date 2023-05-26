package me.rhunk.snapenhance.action.impl

import me.rhunk.snapenhance.action.AbstractAction

class ClearMessageLogger : AbstractAction("action.clear_message_logger") {
    override fun run() {
        context.bridgeClient.clearMessageLogger()
        context.shortToast("Message logger cleared")
    }
}