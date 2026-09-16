package com.code2hack.eyebrowse.phone;

/**
 * Test-only input-safety decision for one synthetic gesture. Unknown IME/geometry is never treated
 * as safe: a missing window-insets object is UNKNOWN, not hidden, and an unavailable target/display
 * fact stops the action before dispatch. Pure JVM logic shared by the JVM regressions and the
 * instrumented tap/swipe paths; never part of the app APK.
 */
final class InputSafety {

    private InputSafety() {}

    /** Tri-state IME visibility: a missing insets object is UNKNOWN, not hidden. */
    enum ImeState {
        VISIBLE,
        HIDDEN,
        UNKNOWN
    }

    static ImeState imeState(boolean insetsAvailable, boolean imeVisible) {
        if (!insetsAvailable) {
            return ImeState.UNKNOWN;
        }
        return imeVisible ? ImeState.VISIBLE : ImeState.HIDDEN;
    }

    /** Screen-coordinate rectangle, with Android's exclusive right/bottom edges. */
    static final class Bounds {
        final int left, top, right, bottom;

        Bounds(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }

        boolean nonEmpty() { return left < right && top < bottom; }
        boolean contains(float x, float y) {
            return nonEmpty() && Float.isFinite(x) && Float.isFinite(y)
                    && x >= left && x < right && y >= top && y < bottom;
        }
        @Override public String toString() {
            return left + "," + top + "-" + right + "," + bottom;
        }
    }

    /** A straight intended gesture; both endpoints inside a rectangle imply the whole segment. */
    static final class Path {
        final float startX, startY, endX, endY;

        Path(float startX, float startY, float endX, float endY) {
            this.startX = startX; this.startY = startY;
            this.endX = endX; this.endY = endY;
        }

        boolean inside(Bounds bounds) {
            return bounds.contains(startX, startY) && bounds.contains(endX, endY);
        }
        boolean overlapsIme(Bounds root, int bottomInset) {
            return Math.max(startY, endY) >= root.bottom - bottomInset;
        }
        @Override public String toString() {
            return startX + "," + startY + "->" + endX + "," + endY;
        }
    }

    /** Complete native mapping/readiness snapshot; tokens compare by identity, not display names. */
    static final class State {
        final Object activity, target;
        final boolean windowFocus, decorAttached, attached, shown, targetFocus;
        final int displayId, x, y, width, height, scrollX, scrollY, imeBottom;
        final Bounds root, visible;
        final ImeState ime;
        final String insets;

        State(Object activity, Object target, boolean windowFocus, boolean decorAttached,
                boolean attached, boolean shown, boolean targetFocus, int displayId,
                int x, int y, int width, int height, int scrollX, int scrollY,
                Bounds root, Bounds visible, ImeState ime, int imeBottom, String insets) {
            this.activity = activity; this.target = target; this.windowFocus = windowFocus;
            this.decorAttached = decorAttached; this.attached = attached; this.shown = shown;
            this.targetFocus = targetFocus; this.displayId = displayId;
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.scrollX = scrollX; this.scrollY = scrollY; this.root = root;
            this.visible = visible; this.ime = ime; this.imeBottom = imeBottom;
            this.insets = insets;
        }

        String unsafeReason(Path path) {
            if (!decorAttached || activity == null || target == null) return "owner/decor unavailable";
            String unsafe = InputSafety.unsafeReason(windowFocus, attached, shown,
                    visible.nonEmpty() && root.nonEmpty() && width > 0 && height > 0,
                    path.inside(visible) && path.inside(root), displayId >= 0, ime,
                    ime == ImeState.VISIBLE && (imeBottom <= 0 || path.overlapsIme(root, imeBottom)));
            return unsafe;
        }

        String mapping() {
            return "focus=" + windowFocus + "/" + targetFocus + " attached=" + decorAttached
                    + "/" + attached + " shown=" + shown + " display=" + displayId
                    + " origin=" + x + "," + y + " size=" + width + "x" + height
                    + " scroll=" + scrollX + "," + scrollY + " root=" + root + " visible="
                    + visible + " ime=" + ime + "/" + imeBottom + " insets=" + insets;
        }

        /** No wait is allowed after this decision and before dispatch. Never reuse stale points. */
        String revalidationReason(State current, Path path) {
            String unsafe = current.unsafeReason(path);
            if (unsafe != null) return unsafe;
            if (activity != current.activity || target != current.target
                    || !mapping().equals(current.mapping())) return "input context changed after preparation";
            return null;
        }
    }

    /** Null when safe; otherwise the first distinct reason to stop before dispatch. */
    static String unsafeReason(boolean windowFocus, boolean targetAttached, boolean targetShown,
            boolean visibleBoundsNonEmpty, boolean pointInsideVisibleBounds, boolean displayKnown,
            ImeState imeState, boolean pointOverlapsIme) {
        if (!windowFocus) {
            return "no window focus";
        }
        if (!targetAttached) {
            return "target WebView not attached";
        }
        if (!targetShown) {
            return "target WebView not shown";
        }
        if (!visibleBoundsNonEmpty) {
            return "target WebView visible bounds empty";
        }
        if (!pointInsideVisibleBounds) {
            return "intended point outside target visible bounds";
        }
        if (!displayKnown) {
            return "display metadata unknown";
        }
        if (imeState == ImeState.UNKNOWN) {
            return "IME state unknown (window insets unavailable)";
        }
        if (pointOverlapsIme) {
            return "IME overlaps the intended gesture";
        }
        return null;
    }
}
