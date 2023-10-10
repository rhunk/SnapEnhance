package me.rhunk.snapenhance.common.util.snap

object BitmojiSelfie {
    enum class BitmojiSelfieType(
        val prefixUrl: String,
    ) {
        STANDARD("https://sdk.bitmoji.com/render/panel/"),
        THREE_D("https://images.bitmoji.com/3d/render/")
    }

    fun getBitmojiSelfie(selfieId: String?, avatarId: String?, type: BitmojiSelfieType): String? {
        if (selfieId.isNullOrEmpty() || avatarId.isNullOrEmpty()) {
            return null
        }
        return when (type) {
            BitmojiSelfieType.STANDARD -> "${type.prefixUrl}$selfieId-$avatarId-v1.webp?transparent=1"
            BitmojiSelfieType.THREE_D -> "${type.prefixUrl}$selfieId-$avatarId-v1.webp?trim=circle"
        }
    }
}