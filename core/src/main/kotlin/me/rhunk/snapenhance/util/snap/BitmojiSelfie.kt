package me.rhunk.snapenhance.util.snap

object BitmojiSelfie {
    enum class BitmojiSelfieType {
        STANDARD,
        THREE_D
    }

    fun getBitmojiSelfie(selfieId: String?, avatarId: String?, type: BitmojiSelfieType): String? {
        if (selfieId.isNullOrEmpty() || avatarId.isNullOrEmpty()) {
            return null
        }
        return when (type) {
            BitmojiSelfieType.STANDARD -> "https://sdk.bitmoji.com/render/panel/$selfieId-$avatarId-v1.webp?transparent=1"
            BitmojiSelfieType.THREE_D -> "https://images.bitmoji.com/3d/render/$selfieId-$avatarId-v1.webp?trim=circle"
        }
    }
}