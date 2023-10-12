package com.sensetime.sensecore.sensecodeplugin.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.keymap.KeymapUtil
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import kotlin.reflect.KClass

object Utils {
    private val WARNING_MISSING_SHORTCUT_KEY = SenseCodeBundle.message("action.utils.warning.missingShortcutKey")


    @JvmStatic
    fun <T : AnAction> getActionID(c: KClass<T>): String? = c.qualifiedName

    @JvmStatic
    fun getAction(actionId: String?): AnAction? = actionId?.let { ActionManager.getInstance().getAction(it) }

    @JvmStatic
    fun <T : AnAction> getAction(c: KClass<T>): AnAction? = getAction(getActionID(c))

    @JvmStatic
    fun getShortcutText(
        action: AnAction?,
        defaultText: String = WARNING_MISSING_SHORTCUT_KEY
    ): String = action?.let { KeymapUtil.getFirstKeyboardShortcutText(it) }?.takeIf { it.isNotBlank() } ?: defaultText

    @JvmStatic
    fun <T : AnAction> getShortcutText(
        c: KClass<T>,
        defaultText: String = WARNING_MISSING_SHORTCUT_KEY
    ): String = getShortcutText(getAction(c), defaultText)
}