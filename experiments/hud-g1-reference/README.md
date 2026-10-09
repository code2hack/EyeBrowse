# HUD-G1 deterministic review reference

Worker: Worker-#30 · issue #30 · I30-T02, attempt 1.

This Kotlin/JVM renderer draws the proposed **H1.2** design at **480 pixels wide ×
640 high**. It is an isolated, non-production design reference. It neither loads
WebView content nor runs Android, camera, input, bookmarks or tab functionality.
Every reference state is **simulated**, including the field/password, QR,
Settings, edge scrolling and author-light/dark/dynamic examples. **HUD-G1 is
OPEN**; these images do not establish Owner approval or HUD-G2 conformance.

The reference is bound to SPEC D2.2 blob
`1acb8ad6d7a0d77d660a5137010efe17ad39ff0a` and HUD H1.2 blob
`070ef8b84d321974af2f35fc68dc1ed2febb91cc`. The renderer refuses changed authority.
The proposed numeric bounds, typography, last-tab behavior and Settings presets
remain subject to the actual design disposition.

## Review artifacts

Open [the complete state index](../../docs/v0.0.2/hud-g1-reference/index.html).
Its captions are outside each app canvas; each linked original PNG is exactly
480×640, opaque RGB, without resizing, cropping, device framing or a watermark
that adds app controls. `manifest.json` binds the source files, fixture seed,
font, images, eight toolbar regions, each illustrated utility/key control and
the pointer footprint. The seed is `eyebrowse-hud-g1-h1.2-20261009-v1`.

There are 18 state IDs and 28 images. Additional images distinguish long
address preview/draft, lowercase/uppercase/symbol layers, text/password focus,
bookmark removal/failure, tracking/loading/error and author-light/dark/dynamic
presentation. All images use one toolbar with the star inside its trailing
address slot, distinct Back/Forward/Refresh, a single 16px pointer and black
first-party fills. Each keyboard image has both Enter/Open and Done. Four-row
More order is fixed. The generated media patch is an explicitly synthetic,
colored fixture; it does not purport to be an actual camera photograph.

The font is pinned **DejaVu Sans** (`DejaVuSans.ttf`), SHA-256
`ae7b7855e115a5966d8b1b3f80f254ccc117ec86f9965e202ee2940453837280`, from the host's
installed DejaVu fonts package. Its license remains with that package; the font
is not copied into the repository. This is a reference sans-serif rendering,
not a claim that Android's actual fonts or measured insets have been qualified.

## Reproduce

Use the assigned shared host-build lock and retain the normal build limits:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/home/code2hack/Android/Sdk \
timeout 900s flock "$HOST_BUILD_LOCK" ./gradlew \
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g --console=plain \
  :experiments:hud-g1-reference:installDist

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 JAVA_OPTS=-Xmx256m \
experiments/hud-g1-reference/build/install/hud-g1-reference/bin/hud-g1-reference \
  "$PWD" "$REFERENCE_OUTPUT_DIRECTORY" \
  /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf
```

Resolve `HOST_BUILD_LOCK` from the mission and check memory before/at that lock.
Use a private output directory for reproduction and compare image hashes to the
committed manifest. The renderer needs no listener, browser, device or network.
It validates state completeness, canvas/toolbar bounds, glyph support,
keyboard bounds/Enter/Done, pointer bounds and predeclared blank black pixels
before emitting artifacts. These are design-reference checks; actual Android
structure, native actions, timing, rendering and optical claims require their
separately authorized evidence.
