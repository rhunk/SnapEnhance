package me.rhunk.snapenhance.core.config


enum class FeatureNotice(
    val id: Int,
    val key: String
) {
    UNSTABLE(0b0001, "unstable"),
    MAY_BAN(0b0010, "may_ban"),
    MAY_BREAK_INTERNAL_BEHAVIOR(0b0100, "may_break_internal_behavior"),
    MAY_CAUSE_CRASHES(0b1000, "may_cause_crashes");
}