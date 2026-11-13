package com.nuvio.app.debug

import co.touchlab.kermit.Logger
import com.nuvio.app.features.playback.animejanai.ActivePlaybackEnhancement
import com.nuvio.app.features.playback.animejanai.AnimeJaNaiPreparationController
import com.nuvio.app.features.playback.animejanai.platformAnimeJaNaiRuntimeControls
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleState
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * Debug-only local control bridge for in-app verification (ui-diagnostics-first).
 *
 * Disabled unless `NUVIO_DEBUG_CONTROL_DIR` names a directory. Then it listens on a random
 * loopback port, writes `endpoint.json` (port + per-run token) into that directory and requires
 * the token in `X-Nuvio-Debug-Token` on every request. It accepts only this fixed command set:
 *
 * - `GET /snapshot` rendered accessibility tree of every visible window (Compose semantics as
 *   exposed to assistive technology): role, name, description, screen bounds, states, actions,
 *   and an index path per node. Bounded by `maxNodes`.
 * - `GET /find?text=` nodes whose name/description contains the text.
 * - `GET /wait?text=&timeoutMs=` waits (bounded, max 30 s) until such a node is rendered.
 * - `POST /action?path=&index=` performs the node's accessibility action (the same semantics
 *   action path assistive technology uses; e.g. Compose onClick), on the AWT event thread.
 * - `POST /settext?path=&text=` sets an editable text node through its accessible editable text.
^ * - `POST /click?x=&y=&button=1|3` AWT click posted into this process at window-relative logical
 *   coordinates (largest window); never moves the OS cursor or touches other windows.
 * - `POST /key?name=` allow-listed key posted to the window's focus owner in this process.
 * - `POST /screenshot?name=` captures the largest window's own content via PrintWindow
 *   (occlusion-independent, physical pixels) into `<dir>/shots/<name>.png`.
 * - `POST /player?event=` allow-listed player controls event (e.g. `videoSettings`), delivered to
 *   the same entry point the WebView2 controls page messages reach.
 * - `GET /status` AnimeJaNai enhancement, runtime and preparation state.
 *
 * Every mutating command bumps the revision returned by all responses.
 */
object DesktopDebugControl {
    private val log = Logger.withTag("DebugControl")
    private val revision = AtomicLong(0)
    private const val MAX_NODES_DEFAULT = 1500
    private val allowedKeys = mapOf(
        "ESCAPE" to KeyEvent.VK_ESCAPE, "ENTER" to KeyEvent.VK_ENTER, "SPACE" to KeyEvent.VK_SPACE,
        "TAB" to KeyEvent.VK_TAB, "LEFT" to KeyEvent.VK_LEFT, "RIGHT" to KeyEvent.VK_RIGHT,
        "UP" to KeyEvent.VK_UP, "DOWN" to KeyEvent.VK_DOWN, "BACK_SPACE" to KeyEvent.VK_BACK_SPACE,
        "F11" to KeyEvent.VK_F11,
    )
    private val allowedPlayerEvents = setOf(
        "cursorActivity", "toggleChrome", "toggle", "seekBack", "seekForward", "back",
        "videoSettings", "sources", "audio", "subtitles", "enhancementAnimationToggle", "enhancementDismissed",
    )

    fun startIfEnabled() {
        val dir = System.getenv("NUVIO_DEBUG_CONTROL_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        runCatching { start(dir) }.onFailure { log.e(it) { "debug control failed to start" } }
    }

    private fun start(dir: File) {
        dir.mkdirs()
        val token = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.executor = Executors.newSingleThreadExecutor { Thread(it, "nuvio-debug-control").apply { isDaemon = true } }
        server.createContext("/") { exchange -> handle(exchange, token, dir) }
        server.start()
        File(dir, "endpoint.json").writeText(
            buildJsonObject {
                put("port", server.address.port)
                put("token", token)
                put("pid", ProcessHandle.current().pid())
            }.toString(),
        )
        log.i { "debug control listening on loopback port ${server.address.port}" }
    }

    private fun handle(exchange: HttpExchange, token: String, dir: File) {
        val response: Pair<Int, JsonElement> = try {
            val supplied = exchange.requestHeaders.getFirst("X-Nuvio-Debug-Token").orEmpty()
            if (!MessageDigest.isEqual(supplied.toByteArray(), token.toByteArray())) {
                403 to errorJson("bad token")
            } else {
                val query = parseQuery(exchange.requestURI.rawQuery)
                val method = exchange.requestMethod
                when (exchange.requestURI.path) {
                    "/snapshot" -> 200 to onEdt { snapshot(query["maxNodes"]?.toIntOrNull() ?: MAX_NODES_DEFAULT) }
                    "/find" -> 200 to onEdt { find(query.getValue("text")) }
                    "/wait" -> 200 to waitFor(query.getValue("text"), query["timeoutMs"]?.toLongOrNull() ?: 10_000)
                    "/status" -> 200 to status()
                    "/action" -> requirePost(method) { onEdt { action(query.getValue("path"), query["index"]?.toIntOrNull() ?: 0) } }
                    "/settext" -> requirePost(method) { onEdt { setText(query.getValue("path"), query.getValue("text")) } }
                    "/click" -> requirePost(method) { click(query.getValue("x").toInt(), query.getValue("y").toInt(), query["button"]?.toIntOrNull() ?: 1) }
                    "/key" -> requirePost(method) { key(query.getValue("name")) }
                    "/screenshot" -> requirePost(method) { screenshot(dir, query["name"] ?: "shot-${revision.get()}") }
                    "/player" -> requirePost(method) { playerEvent(query.getValue("event")) }
                    else -> 404 to errorJson("unknown command")
                }
            }
        } catch (error: NoSuchElementException) {
            400 to errorJson("missing parameter: ${error.message}")
        } catch (error: Throwable) {
            500 to errorJson("${error.javaClass.simpleName}: ${error.message}")
        }
        val body = buildJsonObject {
            put("revision", revision.get())
            put("result", response.second)
        }.toString().toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(response.first, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private inline fun requirePost(method: String, block: () -> JsonElement): Pair<Int, JsonElement> =
        if (method != "POST") 405 to errorJson("use POST") else 200 to block()

    private fun errorJson(message: String): JsonElement = buildJsonObject { put("error", message) }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate { part ->
            val name = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun <T> onEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun windows(): List<Window> = Window.getWindows().filter { it.isShowing }

    private class Node(val path: String, val context: AccessibleContext)

    /** Depth-first walk of the visible windows' accessibility trees, bounded by [maxNodes]. */
    private fun walk(maxNodes: Int, visit: (Node, depth: Int) -> Unit): Boolean {
        var count = 0
        fun recurse(accessible: Accessible, path: String, depth: Int): Boolean {
            if (count >= maxNodes) return false
            val context = accessible.accessibleContext ?: return true
            count++
            visit(Node(path, context), depth)
            for (i in 0 until context.accessibleChildrenCount) {
                val child = context.getAccessibleChild(i) ?: continue
                if (!recurse(child, "$path.$i", depth + 1)) return false
            }
            return true
        }
        return windows().withIndex().all { (index, window) -> recurse(window, "$index", 0) }
    }

    private fun describe(node: Node, depth: Int): JsonObject = buildJsonObject {
        val context = node.context
        put("path", node.path)
        put("depth", depth)
        put("role", context.accessibleRole?.toDisplayString(java.util.Locale.ROOT))
        context.accessibleName?.let { put("name", it) }
        context.accessibleDescription?.let { put("description", it) }
        context.accessibleComponent?.let { component ->
            runCatching {
                val location = component.locationOnScreen
                val size = component.size
                if (location != null && size != null) {
                    put("bounds", buildJsonArray {
                        add(JsonPrimitive(location.x)); add(JsonPrimitive(location.y))
                        add(JsonPrimitive(size.width)); add(JsonPrimitive(size.height))
                    })
                }
            }
        }
        val states = context.accessibleStateSet?.toArray().orEmpty()
            .filter { it in INTERESTING_STATES }
            .map { it.toDisplayString(java.util.Locale.ROOT) }
        if (states.isNotEmpty()) put("states", JsonArray(states.map(::JsonPrimitive)))
        context.accessibleAction?.let { action ->
            val names = (0 until action.accessibleActionCount).map { action.getAccessibleActionDescription(it) ?: "action$it" }
            if (names.isNotEmpty()) put("actions", JsonArray(names.map(::JsonPrimitive)))
        }
        context.accessibleText?.let { text -> runCatching { text.charCount }.getOrNull()?.let { put("textLength", it) } }
    }

    private val INTERESTING_STATES = setOf(
        AccessibleState.ENABLED, AccessibleState.FOCUSED, AccessibleState.SELECTED, AccessibleState.CHECKED,
        AccessibleState.EDITABLE, AccessibleState.SHOWING, AccessibleState.EXPANDED,
    )

    private fun snapshot(maxNodes: Int): JsonElement {
        val nodes = mutableListOf<JsonObject>()
        val complete = walk(maxNodes) { node, depth -> nodes += describe(node, depth) }
        return buildJsonObject {
            put("windows", JsonArray(windows().map { window ->
                buildJsonObject {
                    put("title", (window as? java.awt.Frame)?.title ?: window.name)
                    put("bounds", buildJsonArray {
                        add(JsonPrimitive(window.x)); add(JsonPrimitive(window.y))
                        add(JsonPrimitive(window.width)); add(JsonPrimitive(window.height))
                    })
                }
            }))
            put("complete", complete)
            put("nodeCount", nodes.size)
            put("nodes", JsonArray(nodes))
        }
    }

    private fun matches(context: AccessibleContext, text: String): Boolean =
        listOfNotNull(context.accessibleName, context.accessibleDescription).any { it.contains(text, ignoreCase = true) }

    private fun find(text: String): JsonElement {
        val found = mutableListOf<JsonObject>()
        walk(MAX_NODES_DEFAULT * 4) { node, depth -> if (found.size < 50 && matches(node.context, text)) found += describe(node, depth) }
        return JsonArray(found)
    }

    private fun waitFor(text: String, timeoutMillis: Long): JsonElement {
        val deadline = System.currentTimeMillis() + timeoutMillis.coerceIn(0, 30_000)
        while (true) {
            val found = onEdt { find(text) } as JsonArray
            if (found.isNotEmpty()) return buildJsonObject { put("ready", true); put("nodes", found) }
            if (System.currentTimeMillis() >= deadline) return buildJsonObject { put("ready", false) }
            Thread.sleep(200)
        }
    }

    private fun resolve(path: String): AccessibleContext {
        val indices = path.split('.').map(String::toInt)
        var context: AccessibleContext = windows()[indices.first()].accessibleContext
        for (index in indices.drop(1)) {
            context = context.getAccessibleChild(index)?.accessibleContext ?: error("no node at $path")
        }
        return context
    }

    private fun action(path: String, index: Int): JsonElement {
        val context = resolve(path)
        val action = context.accessibleAction ?: error("node $path has no actions")
        // Read the description first: the node may be disposed by the action it performs.
        val description = action.getAccessibleActionDescription(index)
        val before = revision.getAndIncrement()
        val done = action.doAccessibleAction(index)
        return buildJsonObject {
            put("path", path)
            put("action", description)
            put("performed", done)
            put("revisionBefore", before)
        }
    }

    /**
     * Delivers an allow-listed controls-page event (the WebView2 player controls are not part of
     * the AWT accessibility tree) to the active player's event entry point.
     */
    private fun playerEvent(name: String): JsonElement {
        require(name in allowedPlayerEvents) { "player event not allowed: $name" }
        val sink = com.nuvio.app.features.player.desktop.NativePlayerDebugHooks.activeControlsEvent
            ?: error("no active player")
        revision.incrementAndGet()
        sink(name, 0.0)
        return buildJsonObject { put("event", name) }
    }

    private fun setText(path: String, text: String): JsonElement {
        val editable = resolve(path).accessibleEditableText ?: error("node $path is not editable text")
        revision.incrementAndGet()
        editable.setTextContents(text)
        return buildJsonObject { put("path", path); put("set", true) }
    }

    private fun firstWindow(): Window = windows().maxByOrNull { it.width * it.height } ?: error("no visible window")

    /**
     * Posts a real AWT click into this application's own event queue at window-relative logical
     * coordinates, so it reaches Compose through its normal pointer listeners without moving the
     * OS cursor or touching whichever window is in the foreground.
     */
    private fun click(x: Int, y: Int, button: Int): JsonElement {
        val (awtButton, downMask) = when (button) {
            1 -> MouseEvent.BUTTON1 to InputEvent.BUTTON1_DOWN_MASK
            3 -> MouseEvent.BUTTON3 to InputEvent.BUTTON3_DOWN_MASK
            else -> error("button must be 1 or 3")
        }
        val window = firstWindow()
        val target = SwingUtilities.getDeepestComponentAt(window, x, y) ?: error("no component at $x,$y")
        val point = SwingUtilities.convertPoint(window, x, y, target)
        val queue = java.awt.Toolkit.getDefaultToolkit().systemEventQueue
        val now = System.currentTimeMillis()
        revision.incrementAndGet()
        fun post(id: Int, modifiers: Int, clicks: Int, eventButton: Int, popup: Boolean = false) = queue.postEvent(
            MouseEvent(target, id, now, modifiers, point.x, point.y, clicks, popup, eventButton),
        )
        post(MouseEvent.MOUSE_MOVED, 0, 0, MouseEvent.NOBUTTON)
        post(MouseEvent.MOUSE_PRESSED, downMask, 1, awtButton)
        post(MouseEvent.MOUSE_RELEASED, 0, 1, awtButton, popup = button == 3)
        post(MouseEvent.MOUSE_CLICKED, 0, 1, awtButton)
        return buildJsonObject { put("posted", true); put("component", target.javaClass.simpleName) }
    }

    /** Posts an allow-listed key press/release to the window's focus owner in this process. */
    private fun key(name: String): JsonElement {
        val code = allowedKeys[name.uppercase()] ?: error("key not allowed: $name")
        val window = firstWindow()
        val target = window.mostRecentFocusOwner ?: window
        val queue = java.awt.Toolkit.getDefaultToolkit().systemEventQueue
        val now = System.currentTimeMillis()
        revision.incrementAndGet()
        queue.postEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, code, KeyEvent.CHAR_UNDEFINED))
        queue.postEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, code, KeyEvent.CHAR_UNDEFINED))
        return buildJsonObject { put("key", name.uppercase()); put("component", target.javaClass.simpleName) }
    }

    private interface User32 : com.sun.jna.Library {
        fun GetWindowRect(hwnd: com.sun.jna.Pointer, rect: IntArray): Boolean
        fun GetDC(hwnd: com.sun.jna.Pointer?): com.sun.jna.Pointer?
        fun ReleaseDC(hwnd: com.sun.jna.Pointer?, dc: com.sun.jna.Pointer): Int
        fun PrintWindow(hwnd: com.sun.jna.Pointer, dc: com.sun.jna.Pointer, flags: Int): Boolean
    }

    private interface Gdi32 : com.sun.jna.Library {
        fun CreateCompatibleDC(dc: com.sun.jna.Pointer?): com.sun.jna.Pointer?
        fun CreateCompatibleBitmap(dc: com.sun.jna.Pointer, width: Int, height: Int): com.sun.jna.Pointer?
        fun SelectObject(dc: com.sun.jna.Pointer, obj: com.sun.jna.Pointer): com.sun.jna.Pointer?
        fun GetDIBits(dc: com.sun.jna.Pointer, bitmap: com.sun.jna.Pointer, start: Int, lines: Int, bits: com.sun.jna.Memory, info: com.sun.jna.Memory, usage: Int): Int
        fun DeleteObject(obj: com.sun.jna.Pointer): Boolean
        fun DeleteDC(dc: com.sun.jna.Pointer): Boolean
    }

    private val user32 by lazy { com.sun.jna.Native.load("user32", User32::class.java) }
    private val gdi32 by lazy { com.sun.jna.Native.load("gdi32", Gdi32::class.java) }
    private const val PW_RENDERFULLCONTENT = 2

    /**
     * Captures the application window's own content with PrintWindow(PW_RENDERFULLCONTENT), at
     * physical resolution, regardless of other windows covering it; never reads other screen areas.
     */
    private fun screenshot(dir: File, name: String): JsonElement {
        require(name.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "invalid screenshot name" }
        val hwnd = onEdt { com.sun.jna.Native.getWindowPointer(firstWindow()) } ?: error("no native window handle")
        val rect = IntArray(4)
        check(user32.GetWindowRect(hwnd, rect)) { "GetWindowRect failed" }
        val width = rect[2] - rect[0]
        val height = rect[3] - rect[1]
        require(width in 1..16384 && height in 1..16384) { "bad window size ${width}x$height" }
        val screenDc = user32.GetDC(null) ?: error("GetDC failed")
        val memoryDc = gdi32.CreateCompatibleDC(screenDc) ?: error("CreateCompatibleDC failed")
        val bitmap = gdi32.CreateCompatibleBitmap(screenDc, width, height) ?: error("CreateCompatibleBitmap failed")
        try {
            val previous = gdi32.SelectObject(memoryDc, bitmap)
            val printed = user32.PrintWindow(hwnd, memoryDc, PW_RENDERFULLCONTENT)
            previous?.let { gdi32.SelectObject(memoryDc, it) }
            check(printed) { "PrintWindow failed" }
            val info = com.sun.jna.Memory(44).apply {
                clear()
                setInt(0, 40); setInt(4, width); setInt(8, -height)
                setShort(12, 1); setShort(14, 32); setInt(16, 0)
            }
            val bits = com.sun.jna.Memory(width.toLong() * height * 4)
            check(gdi32.GetDIBits(memoryDc, bitmap, 0, height, bits, info, 0) == height) { "GetDIBits failed" }
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val pixels = bits.getIntArray(0, width * height)
            image.setRGB(0, 0, width, height, pixels, 0, width)
            val file = File(File(dir, "shots").apply { mkdirs() }, "$name.png")
            ImageIO.write(image, "png", file)
            return buildJsonObject {
                put("file", file.absolutePath)
                put("width", width)
                put("height", height)
            }
        } finally {
            gdi32.DeleteObject(bitmap)
            gdi32.DeleteDC(memoryDc)
            user32.ReleaseDC(null, screenDc)
        }
    }

    private fun status(): JsonElement = buildJsonObject {
        val enhancement = ActivePlaybackEnhancement.status.value?.value
        put("enhancement", enhancement?.let {
            buildJsonObject {
                put("kind", it.kind.name)
                put("reason", it.reason)
                put("slot", it.slot)
                if (it.inputWidth != null) put("input", "${it.inputWidth}x${it.inputHeight}")
                if (it.outputWidth != null) put("output", "${it.outputWidth}x${it.outputHeight}")
            }
        } ?: JsonNull)
        val svp = ActivePlaybackEnhancement.svpStatus.value?.value
        put("svp", svp?.let {
            buildJsonObject {
                put("kind", it.kind.name)
                put("reason", it.reason)
                if (it.inputWidth != null) put("input", "${it.inputWidth}x${it.inputHeight}")
                put("sourceFps", it.sourceFps)
                put("outputFps", it.outputFps)
            }
        } ?: JsonNull)
        val runtime = platformAnimeJaNaiRuntimeControls()?.state?.value
        put("runtime", runtime?.let {
            buildJsonObject {
                put("installed", it.installed)
                put("unavailableReason", it.unavailableReason)
                put("activity", it.activity)
                put("error", it.error)
                put("pendingPreparation", it.pendingPreparation)
                put("lastPrepared", it.lastPrepared)
            }
        } ?: JsonNull)
        put("preparation", AnimeJaNaiPreparationController.state.value.toString())
        put("installer", AnimeJaNaiPreparationController.installer.state.value.toString())
    }
}
