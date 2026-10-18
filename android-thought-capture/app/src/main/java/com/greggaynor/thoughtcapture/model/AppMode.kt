package com.greggaynor.thoughtcapture.model

enum class AppMode(
    val title: String,
    val accentColorRes: Int
) {
    ALL("All", com.greggaynor.thoughtcapture.R.color.seed),
    FOCUS("Focus", com.greggaynor.thoughtcapture.R.color.mode_focus),
    WORK("Work", com.greggaynor.thoughtcapture.R.color.mode_work),
    FUN("Fun", com.greggaynor.thoughtcapture.R.color.mode_fun),
    LOW_ENERGY("Low Energy", com.greggaynor.thoughtcapture.R.color.mode_low_energy),
    REVIEW("Review", com.greggaynor.thoughtcapture.R.color.mode_review)
}
