package com.code2hack.eyebrowse.hudreference

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO

/** Fixed design-review drawings only. Does not run Android, a browser, camera or input controls. */
private const val WIDTH = 480
private const val HEIGHT = 640
private const val SEED = "eyebrowse-hud-g1-h1.2-20261009-v1"
private val LIGHT = Color(0xf2, 0xf2, 0xf2)
private val SECONDARY = Color(0xb3, 0xb3, 0xb3)
private val DISABLED = Color(0x8a, 0x8a, 0x8a)
private val OUTLINE = Color(0x60, 0x60, 0x60)

private data class Region(val id: String, val x: Int, val y: Int, val w: Int, val h: Int) {
    fun record() = mapOf("id" to id, "bounds" to listOf(x, y, w, h))
}

private val TARGETS = listOf(
    Region("hud.close_tab", 0, 0, 48, 48),
    Region("hud.back", 48, 0, 48, 48),
    Region("hud.forward", 96, 0, 48, 48),
    Region("hud.refresh", 144, 0, 48, 48),
    Region("hud.address", 192, 0, 128, 48),
    Region("hud.bookmark", 320, 0, 48, 48),
    Region("hud.tab_counter", 368, 0, 64, 48),
    Region("hud.more", 432, 0, 48, 48),
)

private data class Scene(
    val id: String,
    val variant: String,
    val description: String,
    val content: String = "page",
    val bookmarked: Boolean = false,
    val history: Boolean = true,
    val empty: Boolean = false,
    val editing: Boolean = false,
    val keys: Boolean = false,
    val uppercase: Boolean = false,
    val symbols: Boolean = false,
    val field: String? = null,
    val pointerX: Int = 240,
    val pointerY: Int = 300,
    val tracking: Boolean = true,
) {
    val file get() = "simulated-$id-$variant.png"
}

private val SCENES = listOf(
    Scene("G1-01", "browsing", "Browsing: eight actions, second of four, outline star"),
    Scene("G1-02", "saved", "Committed page saved: filled trailing star", bookmarked = true),
    Scene("G1-03", "no-history", "Back/Forward dimmed; Refresh remains present", history = false),
    Scene("G1-04", "long-preview", "Long address clipped within its own 128px region", content = "long"),
    Scene("G1-04", "full-draft", "Full unsent draft scrolls horizontally; star stays fixed", content = "long", editing = true, keys = true),
    Scene("G1-05", "more", "More: Add new tab / Bookmarks / QR scan / Settings", content = "more"),
    Scene("G1-06", "address-keys", "Address keyboard: Open and Done both present", editing = true, keys = true),
    Scene("G1-06", "uppercase", "Shifted English labels, Open and Done", editing = true, keys = true, uppercase = true),
    Scene("G1-06", "symbols", "Digits/common punctuation layer, Open and Done", editing = true, keys = true, symbols = true),
    Scene("G1-07", "text-field", "Illustrated text focus, native-input semantics belong to #32", keys = true, field = "text"),
    Scene("G1-07", "masked-password", "Illustrated settled masked password; not native masking evidence", keys = true, field = "password"),
    Scene("G1-08", "tab-list", "Tab list: selected second of four", content = "tabs"),
    Scene("G1-09", "last-tab-empty", "Proposed last-tab replacement: empty 1/1", content = "empty", empty = true, history = false),
    Scene("G1-10", "bookmarks-empty", "Bookmarks empty utility and Done exit", content = "bookmarks-empty"),
    Scene("G1-11", "bookmarks-list", "Saved entries, explicit Open and Remove", content = "bookmarks-list", bookmarked = true),
    Scene("G1-11", "remove-confirmation", "Explicit bookmark removal illustration", content = "bookmark-remove", bookmarked = true),
    Scene("G1-11", "save-error", "Failed save: truthful outline star, black error", content = "bookmark-error"),
    Scene("G1-12", "qr-preview", "Illustrated camera/decoded URL with explicit Open / Cancel", content = "qr"),
    Scene("G1-13", "qr-unavailable", "Illustrated permission-denied/camera error and recovery", content = "qr-error"),
    Scene("G1-14", "settings", "Illustrated sensitivity/speed presets, Standard selected", content = "settings"),
    Scene("G1-15", "top-edge", "Literal full-screen top contact; illustrated upward page scroll", content = "top-edge", pointerY = 8),
    Scene("G1-16", "bottom-edge-keys", "Literal full-screen bottom contact; page scrolls, keys stay fixed", content = "bottom-edge", keys = true, field = "text", pointerY = 632),
    Scene("G1-17", "tracking-unavailable", "Unavailable ring stays visible; no claimed input or scroll", content = "tracking", tracking = false),
    Scene("G1-17", "loading", "Black backing and local loading status", content = "loading"),
    Scene("G1-17", "network-error", "Recoverable black local error, address controls present", content = "error"),
    Scene("G1-18", "author-light", "Simulated target output for author-light page; intact colored media", content = "author-light"),
    Scene("G1-18", "author-dark", "Simulated target output for author-dark page; intact colored media", content = "author-dark"),
    Scene("G1-18", "dynamic", "Simulated target output after a dynamic update; not engine evidence", content = "dynamic"),
)

private class Drawing(private val font: Font, private val scene: Scene, private val media: BufferedImage) {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    private val g = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
        color = Color.BLACK
        fillRect(0, 0, WIDTH, HEIGHT)
    }
    val keys = mutableListOf<Region>()
    val controls = mutableListOf<Region>()

    fun render(): Drawing {
        content()
        if (scene.keys) keyboard()
        toolbar()
        pointer()
        g.dispose()
        return this
    }

    private fun text(value: String, x: Int, baseline: Int, size: Int = 20, color: Color = LIGHT) {
        g.font = font.deriveFont(size.toFloat())
        g.color = color
        g.drawString(value, x, baseline)
    }

    private fun centered(value: String, region: Region, size: Int = 20, color: Color = LIGHT) {
        g.font = font.deriveFont(size.toFloat())
        val metrics = g.fontMetrics
        text(value, region.x + (region.w - metrics.stringWidth(value)) / 2,
            region.y + (region.h - metrics.height) / 2 + metrics.ascent, size, color)
    }

    private fun box(region: Region, selected: Boolean = false) {
        g.color = Color.BLACK
        g.fillRect(region.x, region.y, region.w, region.h)
        g.color = if (selected) LIGHT else OUTLINE
        g.stroke = BasicStroke(if (selected) 2f else 1f)
        val inset = if (selected) 1 else 0
        g.drawRect(region.x + inset, region.y + inset, region.w - 1 - inset * 2, region.h - 1 - inset * 2)
    }

    private fun button(id: String, label: String, x: Int, y: Int, w: Int, h: Int = 44, selected: Boolean = false) {
        val region = Region(id, x, y, w, h)
        controls += region
        box(region, selected)
        centered(label, region)
    }

    private fun toolbar() {
        for (target in TARGETS) box(target, target.id == "hud.address" && scene.editing)
        val labels = listOf("×", "←", "→", "↻", "", if (scene.bookmarked) "★" else "☆",
            if (scene.empty) "1/1" else "2/4", "⋮")
        TARGETS.forEachIndexed { i, target ->
            if (i != 4) {
                val unavailable = (!scene.history && i in 1..2) || (scene.empty && i in listOf(3, 5))
                centered(labels[i], target, if (i == 6) 20 else 24, if (unavailable) DISABLED else LIGHT)
            }
        }
        val previousClip = g.clip
        g.clipRect(200, 1, 112, 46)
        val address = when {
            scene.empty -> "Address"
            scene.editing -> "/draft?q=demo"
            scene.content == "long" -> "a-very-lo…"
            else -> "fixture.ex…"
        }
        g.font = font.deriveFont(20f)
        val x = if (scene.editing) 309 - g.fontMetrics.stringWidth(address) else 200
        text(address, x, 31, color = if (scene.empty) SECONDARY else LIGHT)
        if (scene.editing) { g.color = LIGHT; g.drawLine(311, 12, 311, 35) }
        g.clip = previousClip
    }

    private fun page() {
        // Dummy website content, not an EyeBrowse title/hero or native implementation claim.
        text("Owned review fixture", 16, 90, 24)
        text("A local page with light readable text.", 16, 126)
        text("Open the next fixture", 16, 164)
        text("Text field", 16, 212, 18, SECONDARY)
        box(Region("fixture.text", 16, 224, 448, 48), scene.field == "text")
        text("Example text", 28, 256)
        text("Password", 16, 302, 18, SECONDARY)
        box(Region("fixture.password", 16, 314, 448, 48), scene.field == "password")
        text("••••••", 28, 346)
        if (!scene.keys) {
            text("Forms, links and media stay on the page.", 16, 416)
            text("More fixture content below", 16, 472)
            text("Each app-owned background is black.", 16, 522, 18, SECONDARY)
        }
    }

    private fun heading(title: String, exit: String = "Done") {
        text(title, 16, 94, 24)
        if (exit.isNotEmpty()) button("utility.exit", exit, 368, 62, 96)
    }

    private fun content() {
        when (scene.content) {
            "page", "long" -> page()
            "top-edge", "bottom-edge" -> {
                page()
                text(if (scene.content == "top-edge") "Upward scroll illustration" else "Downward scroll illustration",
                    16, if (scene.keys) 414 else 572, 18, SECONDARY)
            }
            "more" -> {
                page()
                val labels = listOf("Add new tab", "Bookmarks", "QR scan", "Settings")
                labels.forEachIndexed { index, label ->
                    val region = Region("more.$index", 256, 48 + index * 48, 224, 48)
                    controls += region; box(region); text(label, 268, region.y + 31)
                }
            }
            "tabs" -> {
                heading("Tabs")
                listOf("First fixture", "Current fixture", "Third fixture", "Fourth fixture").forEachIndexed { i, title ->
                    val region = Region("tabs.$i", 16, 124 + i * 76, 448, 68)
                    controls += region; box(region, i == 1)
                    text("${i + 1}   $title", 28, region.y + 29)
                    text(if (i == 1) "Selected · fixture.example" else "fixture.example/tab${i + 1}", 28, region.y + 54, 18, SECONDARY)
                }
            }
            "empty" -> {
                text("Enter an address", 16, 112, 24)
                text("Empty replacement tab · 1/1", 16, 158)
                text("Last-tab behavior is proposed for review.", 16, 202, 18, SECONDARY)
            }
            "bookmarks-empty" -> {
                heading("Bookmarks")
                text("No bookmarks yet", 16, 158)
                text("Save a displayed page with ☆.", 16, 204, 18, SECONDARY)
            }
            "bookmarks-list" -> {
                heading("Bookmarks")
                listOf("Current fixture", "Second saved page").forEachIndexed { index, title ->
                    val top = 136 + index * 164
                    text(title, 16, top + 24)
                    text("fixture.example/saved${index + 1}", 16, top + 58, 18, SECONDARY)
                    button("bookmarks.open.$index", "Open", 16, top + 78, 216)
                    button("bookmarks.remove.$index", "Remove", 248, top + 78, 216)
                }
            }
            "bookmark-remove" -> {
                heading("Bookmarks")
                text("Remove this bookmark?", 16, 166)
                text("Current fixture", 16, 210, 18, SECONDARY)
                button("bookmark.remove", "Remove", 16, 248, 216)
                button("bookmark.cancel", "Cancel", 248, 248, 216)
            }
            "bookmark-error" -> {
                page()
                text("Bookmark was not saved", 16, 574)
                text("Storage unavailable · outline star retained", 16, 610, 18, SECONDARY)
            }
            "qr" -> {
                heading("QR scan", "")
                g.drawImage(media, 32, 126, 416, 192, null)
                text("Illustrative camera preview", 48, 292, 18, Color.WHITE)
                text("Decoded URL", 16, 358, 18, SECONDARY)
                text("https://fixture.example/qr", 16, 394)
                button("qr.open", "Open", 16, 426, 216)
                button("qr.cancel", "Cancel", 248, 426, 216)
                text("Only Open would navigate.", 16, 516, 18, SECONDARY)
            }
            "qr-error" -> {
                heading("QR scan", "")
                text("Camera unavailable", 16, 172, 24)
                text("Permission was not granted.", 16, 218)
                text("Illustration · no permission request ran.", 16, 262, 18, SECONDARY)
                button("qr.retry", "Try again", 16, 306, 216)
                button("qr.cancel", "Cancel", 248, 306, 216)
            }
            "settings" -> {
                heading("Settings")
                text("Pointer sensitivity", 16, 158)
                listOf("Low", "Standard", "High").forEachIndexed { i, label ->
                    button("settings.sensitivity.$i", label, 16 + i * 152, 180, 144, selected = i == 1)
                }
                listOf("0.75×", "1.0×", "1.25×").forEachIndexed { i, label -> text(label, 42 + i * 152, 256, 18, SECONDARY) }
                text("Edge-scroll speed", 16, 318)
                listOf("Slow", "Standard", "Fast").forEachIndexed { i, label ->
                    button("settings.speed.$i", label, 16 + i * 152, 342, 144, selected = i == 1)
                }
                listOf("120", "240", "360").forEachIndexed { i, label -> text("$label px/s", 24 + i * 152, 418, 18, SECONDARY) }
                text("Proposed presets · awaiting HUD-G1", 16, 482, 18, SECONDARY)
            }
            "tracking" -> {
                page()
                text("Tracking unavailable", 16, 574)
                text("Pointer stays visible · motion/scroll paused", 16, 610, 18, SECONDARY)
            }
            "loading" -> {
                text("Loading…", 16, 112, 24)
                text("Black page backing remains visible.", 16, 158, 18, SECONDARY)
            }
            "error" -> {
                text("Page unavailable", 16, 112, 24)
                text("Network request failed.", 16, 158)
                text("Correct the address or deliberately retry.", 16, 204, 18, SECONDARY)
            }
            "author-light", "author-dark", "dynamic" -> {
                text("${scene.content.replace('-', ' ')} fixture", 16, 94, 24)
                text("Target black presentation · simulated", 16, 138, 18, SECONDARY)
                g.drawImage(media, 16, 172, 128, 128, null)
                text("Useful media colors", 164, 212)
                text("No inversion", 164, 254, 18, SECONDARY)
                box(Region("fixture.readable-field", 16, 334, 448, 48))
                text("Readable native field preview", 28, 367)
                button("fixture.dynamic", "Update fixture", 16, 420, 244)
                text(if (scene.content == "dynamic") "Updated dynamic content" else "Ordinary document content", 16, 514)
                text("Actual engine output requires RG tests.", 16, 562, 18, SECONDARY)
            }
            else -> error("Unknown reference content ${scene.content}")
        }
    }

    private fun keyRow(labels: List<Pair<String, String>>, y: Int, h: Int, widths: List<Int>? = null) {
        val available = 464 - (labels.size - 1) * 4
        var x = 8
        labels.forEachIndexed { index, (id, label) ->
            val w = widths?.get(index) ?: ((index + 1) * available / labels.size - index * available / labels.size)
            val region = Region("keyboard.$id", x, y, w, h)
            keys += region; box(region)
            centered(label, region, if (id == "shift" || id == "backspace") 24 else 20)
            x += w + 4
        }
        check(x - 4 == 472) { "Keyboard row must finish at reference x472" }
    }

    private fun keyboard() {
        val row1 = if (scene.symbols) "1234567890" else if (scene.uppercase) "QWERTYUIOP" else "qwertyuiop"
        val row2 = if (scene.symbols) ":-@_?&=#%" else if (scene.uppercase) "ASDFGHJKL" else "asdfghjkl"
        val row3 = if (scene.symbols) "+,;!'\"()" else if (scene.uppercase) "ZXCVBNM" else "zxcvbnm"
        fun letters(s: String) = s.map { "character.$it" to it.toString() }
        keyRow(letters(row1), 444, 44)
        keyRow(letters(row2), 492, 44)
        keyRow(listOf("shift" to "⇧") + letters(row3) + listOf("backspace" to "⌫"), 540, 44)
        keyRow(listOf("symbols" to if (scene.symbols) "ABC" else "123", "space" to "Space",
            "character.dot" to ".", "character.slash" to "/", "enter" to if (scene.editing) "Open" else "Enter",
            "done" to "Done"), 588, 48, listOf(52, 148, 36, 36, 96, 76))
    }

    private fun pointer() {
        check(scene.pointerX in 8..472 && scene.pointerY in 8..632)
        g.color = if (scene.tracking) LIGHT else DISABLED
        g.stroke = BasicStroke(2f)
        g.drawOval(scene.pointerX - 7, scene.pointerY - 7, 14, 14)
        g.fillOval(scene.pointerX - 1, scene.pointerY - 1, 3, 3)
    }
}

private fun sha(bytes: ByteArray, algorithm: String = "SHA-256") =
    MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

private fun blobSha(path: Path): String {
    val bytes = Files.readAllBytes(path)
    return sha("blob ${bytes.size}\u0000".toByteArray() + bytes, "SHA-1")
}

private fun json(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.flatMap { ch -> when (ch) {
        '\\' -> "\\\\".toList(); '"' -> "\\\"".toList(); '\n' -> "\\n".toList(); '\r' -> "\\r".toList(); '\t' -> "\\t".toList()
        else -> listOf(ch)
    } }.joinToString("") + "\""
    is Number, is Boolean -> value.toString()
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
    is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
    else -> error("Unsupported reference metadata type")
}

private fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** repo-root, output-directory, exact DejaVuSans.ttf path. No services or Android device calls. */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: hud-g1-reference REPO_ROOT OUTPUT_DIRECTORY DEJAVU_SANS_TTF" }
    System.setProperty("java.awt.headless", "true")
    val repo = Path.of(args[0]).toAbsolutePath().normalize()
    val output = Path.of(args[1]).toAbsolutePath().normalize()
    val fontFile = Path.of(args[2]).toAbsolutePath().normalize()
    val fontBytes = Files.readAllBytes(fontFile)
    val fontHash = sha(fontBytes)
    require(fontHash == "ae7b7855e115a5966d8b1b3f80f254ccc117ec86f9965e202ee2940453837280") {
        "Use the pinned DejaVuSans.ttf for this exact review reference"
    }
    val font = Font.createFont(Font.TRUETYPE_FONT, fontBytes.inputStream())
    require(font.canDisplayUpTo("×←→↻☆★⋮⇧⌫•••") == -1) { "Reference font cannot draw a required control" }
    val spec = blobSha(repo.resolve("SPEC.md"))
    val hud = blobSha(repo.resolve("docs/design/v0.0.2-rg-hud.md"))
    require(spec == "1acb8ad6d7a0d77d660a5137010efe17ad39ff0a" && hud == "070ef8b84d321974af2f35fc68dc1ed2febb91cc") {
        "Reference requires its exact pinned D2.2/H1.2 contract; reconcile changed authority first"
    }
    require(TARGETS.map { it.x } == listOf(0, 48, 96, 144, 192, 320, 368, 432))
    require(TARGETS.all { it.y == 0 && it.h == 48 } && TARGETS.last().x + TARGETS.last().w == WIDTH)
    require(SCENES.map { it.id }.toSet() == (1..18).map { "G1-%02d".format(it) }.toSet())
    require(SCENES.map { it.file }.distinct().size == SCENES.size)
    Files.createDirectories(output)
    val media = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB).apply {
        val g = createGraphics()
        g.color = Color(0xe0, 0x40, 0x40); g.fillRect(0, 0, 64, 64)
        g.color = Color(0x30, 0x90, 0xe0); g.fillRect(0, 32, 64, 32)
        g.color = Color(0xf0, 0xc0, 0x40); g.fillOval(24, 8, 16, 16)
        g.dispose()
    }
    ImageIO.write(media, "png", output.resolve("simulated-media-fixture.png").toFile())
    val sourcePaths = listOf("experiments/hud-g1-reference/build.gradle.kts",
        "experiments/hud-g1-reference/src/main/kotlin/com/code2hack/eyebrowse/hudreference/HudReference.kt")
    val sources = sourcePaths.associateWith { sha(Files.readAllBytes(repo.resolve(it))) }
    val records = SCENES.map { scene ->
        val drawing = Drawing(font, scene, media).render()
        // Declared blank app regions, chosen before rendering. Media/glyphs are not backgrounds.
        val samples = listOf(3 to 3, 195 to 3, 323 to 3, 435 to 3, 479 to 639)
        require(samples.all { (x, y) -> (drawing.image.getRGB(x, y) and 0xffffff) == 0 }) { "Nonblack app backing in ${scene.file}" }
        require(drawing.keys.all { it.x >= 8 && it.x + it.w <= 472 && it.y >= 444 && it.y + it.h <= 636 })
        if (scene.keys) require(drawing.keys.any { it.id == "keyboard.enter" } && drawing.keys.any { it.id == "keyboard.done" })
        val file = output.resolve(scene.file)
        check(ImageIO.write(drawing.image, "png", file.toFile()))
        mapOf("state" to scene.id, "variant" to scene.variant, "file" to scene.file,
            "description" to scene.description, "classification" to "SIMULATED_NON_PRODUCTION_DESIGN_REFERENCE",
            "width" to WIDTH, "height" to HEIGHT, "sha256" to sha(Files.readAllBytes(file)),
            "toolbarTargets" to TARGETS.map { it.record() }, "utilityControls" to drawing.controls.map { it.record() },
            "keys" to drawing.keys.map { it.record() }, "pointer" to mapOf("x" to scene.pointerX, "y" to scene.pointerY, "footprint" to 16),
            "keyboardVisible" to scene.keys, "bookmarkedIllustration" to scene.bookmarked,
            "fullAddressDraftFixture" to if (scene.editing) "https://fixture.example/a/long/draft?q=demo" else null)
    }
    val manifest = mapOf("status" to "NON_PRODUCTION_REFERENCE_HUD_G1_OPEN", "fixtureSeed" to SEED,
        "specRevision" to "D2.2", "specGitBlob" to spec, "hudRevision" to "H1.2", "hudGitBlob" to hud,
        "width" to WIDTH, "height" to HEIGHT, "referenceCoordinatesArePixels" to true,
        "font" to mapOf("family" to font.family, "sha256" to fontHash, "filename" to fontFile.fileName.toString()),
        "renderer" to "Kotlin/JVM Java2D; NOT Android View/WebView capture",
        "javaRuntime" to System.getProperty("java.runtime.version"), "sourceFilesSha256" to sources,
        "sourceDigestSha256" to sha(sources.entries.joinToString("\n") { "${it.key}:${it.value}" }.toByteArray()),
        "mediaFixtureSha256" to sha(Files.readAllBytes(output.resolve("simulated-media-fixture.png"))),
        "stateCount" to 18, "imageCount" to records.size, "artifacts" to records)
    Files.writeString(output.resolve("manifest.json"), json(manifest) + "\n")
    val figures = records.joinToString("\n") { record ->
        "<figure><a href=\"${html(record["file"].toString())}\"><img width=\"480\" height=\"640\" src=\"${html(record["file"].toString())}\" alt=\"${html(record["description"].toString())}\"></a>" +
            "<figcaption><b>${record["state"]} · ${record["variant"]}</b><br>${html(record["description"].toString())}<br>SIMULATED · non-production · 480×640</figcaption></figure>"
    }
    Files.writeString(output.resolve("index.html"), """<!doctype html><html lang="en"><meta charset="utf-8">
        <title>EyeBrowse HUD-G1 simulated review reference</title>
        <style>body{background:#111;color:#eee;font:16px sans-serif;margin:24px}a{color:#ddd}.grid{display:flex;flex-wrap:wrap;gap:24px}figure{margin:0;width:480px}img{display:block;width:480px;height:640px}figcaption{padding:12px 0;line-height:1.5}p{max-width:1000px}</style>
        <h1>HUD-G1 design reference · OPEN</h1>
        <p>All 18 state IDs, ${records.size} native-size reference drawings. Every displayed browser, tab, bookmark, keyboard, QR, Settings, edge and dark-page state is simulated. These images demonstrate proposed layout only; they are not functioning controls, Android/device captures, engine/masking proof, HUD-G1 Owner approval or HUD-G2 acceptance. Captions and this index surround the canvas and are not app UI.</p>
        <p>480 pixels wide × 640 high portrait; original PNGs are linked individually. D2.2 / H1.2. Fixture seed $SEED. <a href="manifest.json">Exact source/font/image hashes and control bounds</a>.</p>
        <div class="grid">$figures</div></html>
    """.trimIndent() + "\n")
    println("Generated ${records.size} simulated480x640 reference PNGs for all18 states; HUD-G1 remains OPEN.")
    println("Source digest: ${manifest["sourceDigestSha256"]}; font SHA-256: $fontHash")
}
