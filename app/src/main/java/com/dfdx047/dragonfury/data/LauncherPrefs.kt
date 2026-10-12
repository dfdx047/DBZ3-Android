package com.dfdx047.dragonfury.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode(val key: String) { DRAGON("dragon"), MATERIAL_YOU("material_you") }
enum class DarkMode(val key: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }
enum class AppLanguage(val tag: String) { SYSTEM(""), PORTUGUESE("pt"), ENGLISH("en") }

/** Orientacao do jogo. Os valores sao lidos tambem pelo GameActivity (Java). */
enum class GameOrientation(val key: String) { LANDSCAPE("landscape"), REVERSE("reverse"), AUTO("auto") }

/** Aspeto dos controles por toque. */
enum class ControlStyle(val key: String) { DRAGON("dragon"), XBOX("xbox"), PLAYSTATION("playstation") }

inline fun <reified T : Enum<T>> enumByKey(value: String?, default: T, key: (T) -> String): T =
    enumValues<T>().firstOrNull { key(it) == value } ?: default

/**
 * Preferencias do launcher (nao do jogo: essas vivem no dbz3_user.toml).
 * O GameActivity le as mesmas chaves (PREFS_NAME, KEY_*).
 */
class LauncherPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = enumByKey(prefs.getString(KEY_THEME, null), ThemeMode.DRAGON) { it.key }
        set(v) = prefs.edit().putString(KEY_THEME, v.key).apply()

    var darkMode: DarkMode
        get() = enumByKey(prefs.getString(KEY_DARK, null), DarkMode.SYSTEM) { it.key }
        set(v) = prefs.edit().putString(KEY_DARK, v.key).apply()

    var gameOrientation: GameOrientation
        get() = enumByKey(prefs.getString(KEY_ORIENTATION, null), GameOrientation.LANDSCAPE) { it.key }
        set(v) = prefs.edit().putString(KEY_ORIENTATION, v.key).apply()

    var controlStyle: ControlStyle
        get() = enumByKey(prefs.getString(KEY_CONTROL_STYLE, null), ControlStyle.DRAGON) { it.key }
        set(v) = prefs.edit().putString(KEY_CONTROL_STYLE, v.key).apply()

    var touchEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_TOUCH_ENABLED, v).apply()

    var touchHideWithPad: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_HIDE_WITH_PAD, true)
        set(v) = prefs.edit().putBoolean(KEY_TOUCH_HIDE_WITH_PAD, v).apply()

    var touchHaptics: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_HAPTICS, true)
        set(v) = prefs.edit().putBoolean(KEY_TOUCH_HAPTICS, v).apply()

    /** Opacidade global dos controles (0.1-1), multiplica a de cada botao. */
    var touchOpacity: Float
        get() = prefs.getFloat(KEY_TOUCH_OPACITY, 0.75f)
        set(v) = prefs.edit().putFloat(KEY_TOUCH_OPACITY, v).apply()

    var turnipNoticeShown: Boolean
        get() = prefs.getBoolean(KEY_TURNIP_NOTICE, false)
        set(v) = prefs.edit().putBoolean(KEY_TURNIP_NOTICE, v).apply()

    var checkUpdatesOnStart: Boolean
        get() = prefs.getBoolean(KEY_CHECK_UPDATES, true)
        set(v) = prefs.edit().putBoolean(KEY_CHECK_UPDATES, v).apply()

    /** Versao que o usuario mandou ignorar no pop-up de atualizacao. */
    var skippedUpdate: String?
        get() = prefs.getString(KEY_SKIPPED_UPDATE, null)
        set(v) = prefs.edit().putString(KEY_SKIPPED_UPDATE, v).apply()

    companion object {
        const val PREFS_NAME = "dragonfury"
        const val KEY_THEME = "theme"
        const val KEY_DARK = "dark_mode"
        const val KEY_ORIENTATION = "game_orientation"
        const val KEY_CONTROL_STYLE = "control_style"
        const val KEY_TOUCH_ENABLED = "touch_enabled"
        const val KEY_TOUCH_HIDE_WITH_PAD = "touch_hide_with_pad"
        const val KEY_TOUCH_HAPTICS = "touch_haptics"
        const val KEY_TOUCH_OPACITY = "touch_opacity"
        const val KEY_TURNIP_NOTICE = "turnip_notice_shown"
        const val KEY_CHECK_UPDATES = "check_updates"
        const val KEY_SKIPPED_UPDATE = "skipped_update"
    }
}
