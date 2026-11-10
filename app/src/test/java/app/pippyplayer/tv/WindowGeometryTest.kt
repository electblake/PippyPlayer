package app.pippyplayer.tv
import org.junit.Assert.*
import org.junit.Test
class WindowGeometryTest {
    @Test fun landscapeFits() { assertEquals(576 to 324, WindowGeometry.dimensions(1920, 1080, 30, 16f/9f)) }
    @Test fun portraitFitsHeight() { assertEquals(182 to 324, WindowGeometry.dimensions(1920, 1080, 30, 9f/16f)) }
    @Test fun squarePreservesAspect() { assertEquals(540 to 540, WindowGeometry.dimensions(1920, 1080, 50, 1f)) }
    @Test fun ultraWideFitsWidth() { assertEquals(1728 to 432, WindowGeometry.dimensions(1920, 1080, 90, 4f)) }
}
