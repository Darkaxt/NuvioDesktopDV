package com.nuvio.app.core.ui

import androidx.compose.ui.input.pointer.PointerEvent

internal actual fun PointerEvent.isMouseBackPress(): Boolean = false

internal actual fun PointerEvent.isMouseForwardPress(): Boolean = false
