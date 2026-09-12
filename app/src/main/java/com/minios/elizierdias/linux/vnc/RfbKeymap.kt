/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */
package com.minios.elizierdias.linux.vnc

import android.view.KeyEvent

/**
 * Mapeia teclas Android → X11 keysym (RFB KeyEvent).
 */
object RfbKeymap {

    fun keysym(event: KeyEvent): Int {
        // Unicode first (letters, digits, punctuation with modifiers)
        val unicode = event.unicodeChar
        if (unicode != 0 && unicode and KeyEvent.META_UNICODE_CHAR_FLAG.inv() != 0) {
            val ch = unicode and KeyEvent.META_UNICODE_CHAR_FLAG.inv()
            if (ch in 0x20..0x7E) return ch
            if (ch > 0x7E) return ch // Latin-1 / BMP subset
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0xff0d // Return
            KeyEvent.KEYCODE_DEL -> 0xff08 // BackSpace
            KeyEvent.KEYCODE_FORWARD_DEL -> 0xffff // Delete
            KeyEvent.KEYCODE_TAB -> 0xff09
            KeyEvent.KEYCODE_ESCAPE -> 0xff1b
            KeyEvent.KEYCODE_SPACE -> 0x20
            KeyEvent.KEYCODE_DPAD_LEFT -> 0xff51
            KeyEvent.KEYCODE_DPAD_UP -> 0xff52
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0xff53
            KeyEvent.KEYCODE_DPAD_DOWN -> 0xff54
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_HOME -> 0xff50 // Home
            KeyEvent.KEYCODE_MOVE_END -> 0xff57 // End
            KeyEvent.KEYCODE_PAGE_UP -> 0xff55
            KeyEvent.KEYCODE_PAGE_DOWN -> 0xff56
            KeyEvent.KEYCODE_INSERT -> 0xff63
            KeyEvent.KEYCODE_F1 -> 0xffbe
            KeyEvent.KEYCODE_F2 -> 0xffbf
            KeyEvent.KEYCODE_F3 -> 0xffc0
            KeyEvent.KEYCODE_F4 -> 0xffc1
            KeyEvent.KEYCODE_F5 -> 0xffc2
            KeyEvent.KEYCODE_F6 -> 0xffc3
            KeyEvent.KEYCODE_F7 -> 0xffc4
            KeyEvent.KEYCODE_F8 -> 0xffc5
            KeyEvent.KEYCODE_F9 -> 0xffc6
            KeyEvent.KEYCODE_F10 -> 0xffc7
            KeyEvent.KEYCODE_F11 -> 0xffc8
            KeyEvent.KEYCODE_F12 -> 0xffc9
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xffe1
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0xffe3
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 0xffe9
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> 0xffe7
            KeyEvent.KEYCODE_CAPS_LOCK -> 0xffe5
            else -> 0
        }
    }
}
