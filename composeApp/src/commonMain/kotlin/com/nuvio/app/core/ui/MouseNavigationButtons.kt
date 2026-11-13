package com.nuvio.app.core.ui

import androidx.compose.ui.input.pointer.PointerEvent

// PointerEvent.button / PointerButton are desktop-only Compose APIs, so the
// mouse back/forward button checks are resolved per platform.
internal expect fun PointerEvent.isMouseBackPress(): Boolean

internal expect fun PointerEvent.isMouseForwardPress(): Boolean
