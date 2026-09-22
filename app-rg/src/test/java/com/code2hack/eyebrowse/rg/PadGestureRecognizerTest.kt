package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.rg.PadGestureRecognizer.*
import org.junit.Assert.*
import org.junit.Test

class PadGestureRecognizerTest {
    private class Trial {
        var target:String?="original"
        var captures=0
        val singles=mutableListOf<String?>()
        var modes=0
        val scrolls=mutableListOf<Int>()
        val recognizer=PadGestureRecognizer(300,500,{captures++;target},{singles.add(it)},{modes++},{scrolls.add(it)})
        fun event(key: Key, phase: Phase, at:Long, down:Long=at, repeat:Int=0, receipt:Long=at) =
            recognizer.accept(Event(key,phase,at,down,repeat),receipt)
        fun tap(at:Long,key:Key=Key.TAP) { event(key,Phase.DOWN,at);event(key,Phase.UP,at+20,at) }
    }
    @Test fun singleCapturesAtUpAndConfirmsOnceAtExactDeadline() {
        val t=Trial();t.event(Key.TAP,Phase.DOWN,1000);assertEquals(0,t.captures)
        t.target="at up";t.event(Key.TAP,Phase.UP,1020,1000);t.target="later"
        t.recognizer.confirm(1319);assertTrue(t.singles.isEmpty())
        t.recognizer.confirm(1320);t.recognizer.confirm(5000)
        assertEquals(listOf("at up"),t.singles);assertEquals(1,t.captures)
    }
    @Test fun doubleTapConsumesBothSinglesEvenWithoutAnEligibleTarget() {
        val t=Trial();t.target=null;t.tap(1000);t.tap(1100);t.recognizer.confirm(2000)
        assertEquals(1,t.modes);assertTrue(t.singles.isEmpty())
    }
    @Test fun oemDoubleConsumesPendingTapAndItsDownUpPairOnlyOnce() {
        val t=Trial();t.tap(1000);t.tap(1100,Key.DOUBLE);t.recognizer.confirm(2000)
        assertEquals(1,t.modes);assertTrue(t.singles.isEmpty())
    }
    @Test fun compositeTwoEntersPlusOemDoubleProduceOneModeIntent() {
        val t=Trial();t.tap(1000);t.tap(1100);t.tap(1130,Key.DOUBLE);t.tap(1160)
        t.recognizer.confirm(2000);assertEquals(1,t.modes);assertTrue(t.singles.isEmpty())
    }
    @Test fun swipeSupersedesTapAndDoesNotLeakTrailingEnter() {
        val t=Trial();t.tap(1000);t.tap(1100,Key.FORWARD);t.tap(1150)
        t.recognizer.confirm(2000);assertEquals(listOf(160),t.scrolls);assertTrue(t.singles.isEmpty())
        t.tap(2200,Key.BACKWARD);assertEquals(listOf(160,-160),t.scrolls)
    }
    @Test fun repeatedDownUpDoesNotProduceRepeatedClicksOrScrolls() {
        val t=Trial();t.event(Key.TAP,Phase.DOWN,1000);t.event(Key.TAP,Phase.DOWN,1001,1000)
        t.event(Key.TAP,Phase.UP,1020,1000);t.event(Key.TAP,Phase.UP,1021,1000);t.recognizer.confirm(2000)
        assertEquals(1,t.singles.size)
        t.tap(2200,Key.FORWARD);t.event(Key.FORWARD,Phase.UP,2221,2200);assertEquals(listOf(160),t.scrolls)
    }
    @Test fun keyRepeatAndLongHoldCancelWithoutTapOrSwipe() {
        val t=Trial();t.event(Key.TAP,Phase.DOWN,1000);t.event(Key.TAP,Phase.DOWN,1200,1000,1)
        t.event(Key.TAP,Phase.UP,1400,1000);t.recognizer.confirm(2000);assertTrue(t.singles.isEmpty())
        t.event(Key.FORWARD,Phase.DOWN,2100);t.event(Key.FORWARD,Phase.UP,2600,2100);assertTrue(t.scrolls.isEmpty())
    }
    @Test fun cancelledSequenceAndExternalInvalidationNeverReplay() {
        val t=Trial();t.tap(1000);t.recognizer.cancel();t.recognizer.confirm(2000)
        t.event(Key.TAP,Phase.DOWN,2100);t.event(Key.TAP,Phase.CANCEL,2110,2100);t.event(Key.TAP,Phase.UP,2120,2100)
        t.recognizer.confirm(3000);assertTrue(t.singles.isEmpty());assertFalse(t.recognizer.hasWork)
    }
    @Test fun upWithoutMatchingDownIsNotAConfirmedTap() {
        val t=Trial();t.event(Key.TAP,Phase.UP,1020,1000)
        t.event(Key.TAP,Phase.DOWN,1100);t.event(Key.TAP,Phase.UP,1120,1099)
        t.recognizer.confirm(2000);assertTrue(t.singles.isEmpty())
    }
    @Test fun staleFutureAndOutOfOrderEventsCannotCaptureOrDispatch() {
        val t=Trial();t.event(Key.TAP,Phase.DOWN,1000,receipt=1251)
        t.event(Key.TAP,Phase.UP,1020,1000,receipt=1252)
        t.event(Key.TAP,Phase.DOWN,1400,receipt=1399)
        t.tap(2000);t.event(Key.TAP,Phase.DOWN,1999);t.recognizer.confirm(4000)
        assertEquals(1,t.captures);assertTrue(t.singles.isEmpty())
    }
    @Test fun timeoutBoundaryStartsANewGestureWithoutMergingUnrelatedSingles() {
        val t=Trial();t.tap(1000);t.tap(1320);t.recognizer.confirm(1640)
        assertEquals(listOf("original","original"),t.singles);assertEquals(0,t.modes)
    }
    @Test fun secondPressBecomingLongHoldDoesNotReleaseTheFirstTap() {
        val t=Trial();t.tap(1000);t.event(Key.TAP,Phase.DOWN,1100)
        t.recognizer.confirm(1400);t.event(Key.TAP,Phase.UP,1600,1100);t.recognizer.confirm(2000)
        assertTrue(t.singles.isEmpty());assertEquals(0,t.modes)
    }
    @Test fun malformedNegativeDownTimeCannotOverflowIntoAShortTap() {
        val t=Trial();t.event(Key.TAP,Phase.DOWN,1000,Long.MIN_VALUE)
        t.event(Key.TAP,Phase.UP,1020,Long.MIN_VALUE);t.recognizer.confirm(2000)
        assertEquals(0,t.captures);assertTrue(t.singles.isEmpty())
    }
}
