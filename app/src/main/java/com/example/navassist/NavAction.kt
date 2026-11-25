package com.example.navassist

/** Every button that can appear in the floating menu. */
enum class NavAction(
    val label: String,
    val icon: Int,
    val keepOpen: Boolean = false,
    val default: Boolean = true
) {
    HOME("Home", R.drawable.ic_home),
    BACK("Back", R.drawable.ic_back),
    RECENTS("Recents", R.drawable.ic_recents),
    NOTIFICATIONS("Notifications", R.drawable.ic_notifications),
    QUICK_SETTINGS("Quick panel", R.drawable.ic_apps),
    LOCK("Lock", R.drawable.ic_lock),
    POWER("Power", R.drawable.ic_power),
    SCREENSHOT("Screenshot", R.drawable.ic_crop),
    VOLUME_UP("Vol +", R.drawable.ic_volume_up, keepOpen = true),
    VOLUME_DOWN("Vol −", R.drawable.ic_volume_down, keepOpen = true),
    MIXER("Mixer", R.drawable.ic_tune, keepOpen = true),
    SEARCH("Search", R.drawable.ic_search),
    AI("AI", R.drawable.ic_sparkle),
    CAMERA("Camera", R.drawable.ic_camera),
    GALLERY("Gallery", R.drawable.ic_gallery),
    SETTINGS("Settings", R.drawable.ic_settings),
    SELFIE("Selfie", R.drawable.ic_flip_camera, default = false),
    TORCH("Torch", R.drawable.ic_flashlight, keepOpen = true, default = false),
    DATA("Data / Wi-Fi", R.drawable.ic_data, default = false),
    FAVORITE("Favorite", R.drawable.ic_heart, default = false);

    companion object {
        val defaults: List<NavAction> get() = values().filter { it.default }
    }
}
