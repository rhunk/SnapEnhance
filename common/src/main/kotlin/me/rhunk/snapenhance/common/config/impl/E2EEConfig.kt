package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer

class E2EEConfig : ConfigContainer(hasGlobalState = true) {
    val encryptedMessageIndicator = boolean("encrypted_message_indicator")
    val forceMessageEncryption = boolean("force_message_encryption")
}