package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class PointerImageGeometryTest {
    private fun geometry(matrix:List<Float> = listOf(2f,0f,-40f,0f,2f,-200f,0f,0f,1f)) =
        PointerImageGeometry(matrix,800,600,PointerBounds(20f,100f,420f,400f),PresentationProfile(800,600,160))
    @Test fun parentOffsetPaddingAndMatrixScaleMapToNativePixels() {
        assertEquals(InputPoint(200f,100f),geometry().pagePoint(InputPoint(120f,150f)))
    }
    @Test fun letterboxAndExclusiveImageEdgesRejectInsteadOfClamping() {
        val g=geometry().copy(visibleContent=PointerBounds(0f,0f,480f,500f))
        for(p in listOf(InputPoint(19f,150f),InputPoint(120f,99f),InputPoint(420f,150f),InputPoint(120f,400f))) assertNull(g.pagePoint(p))
    }
    @Test fun densityScaledDrawableMapsToProfileWithoutDensityGuess() {
        val g=geometry().copy(profile=PresentationProfile(400,300,300))
        assertEquals(InputPoint(100f,50f),g.pagePoint(InputPoint(120f,150f)))
    }
    @Test fun clipBoundsAndInvalidCoordinatesCannotProducePageTargets() {
        val g=geometry().copy(visibleContent=PointerBounds(30f,120f,400f,390f))
        assertNull(g.pagePoint(InputPoint(25f,150f)));assertNull(g.pagePoint(InputPoint(Float.NaN,150f)))
        assertNull(geometry().copy(rootToDrawable=List(9){0f}).pagePoint(InputPoint(120f,150f)))
    }
    @Test fun rotatedImageUsesBothMatrixAxes() {
        val g=PointerImageGeometry(listOf(0f,1f,0f,-1f,0f,400f,0f,0f,1f),600,400,
            PointerBounds(0f,0f,400f,600f),PresentationProfile(600,400,160))
        assertEquals(InputPoint(200f,300f),g.pagePoint(InputPoint(100f,200f)))
    }
}
