package com.example.navassist

/** Every button that can appear in the floating menu or be assigned to an orb gesture. */
enum class NavAction(
    val label: String,
    val icon: Int,
    val keepOpen: Boolean = false,
    val default: Boolean = true
) {
    HOME("Home", R.drawable.ic_home),
    BACK("Back", R.drawable.ic_back),
    RECENTS("Recents", R.drawable.ic_recents),
    RECENT_APPS("Recent apps", R.drawable.ic_history, default = false),
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
    WIFI("Wi-Fi", R.drawable.ic_data, default = false),
    DATA("Internet", R.drawable.ic_swap, default = false),
    BLUETOOTH("Bluetooth", R.drawable.ic_bluetooth, default = false),
    LOCATION("Location", R.drawable.ic_location, default = false),
    AIRPLANE("Airplane", R.drawable.ic_airplane, default = false),
    BRIGHTNESS("Brightness", R.drawable.ic_brightness, keepOpen = true, default = false),
    TIMEOUT("Timeout", R.drawable.ic_timer, keepOpen = true, default = false),
    ROTATION("Rotation", R.drawable.ic_rotation, keepOpen = true, default = false),
    SOUND_MODE("Sound mode", R.drawable.ic_notifications, keepOpen = true, default = false),
    RECORDER("Recorder", R.drawable.ic_record, default = false),
    STORAGE("Storage", R.drawable.ic_folder, default = false),
    HIDE("Hide orb", R.drawable.ic_visibility_off, default = false),
    FAVORITE("Favorites", R.drawable.ic_star, keepOpen = true, default = false);

    companion object {
        val defaults: List<NavAction> get() = values().filter { it.default }
    }
}
