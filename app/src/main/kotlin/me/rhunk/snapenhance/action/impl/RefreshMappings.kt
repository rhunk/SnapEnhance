package me.rhunk.snapenhance.action.impl

import me.rhunk.snapenhance.action.AbstractAction
import me.rhunk.snapenhance.bridge.types.BridgeFileType

class RefreshMappings : AbstractAction("action.refresh_mappings") {
    override fun run() {
        context.bridgeClient.deleteFile(BridgeFileType.MAPPINGS)
        context.softRestartApp()
    }
}