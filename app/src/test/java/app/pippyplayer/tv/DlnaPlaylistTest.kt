package app.pippyplayer.tv

import java.net.URI
import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class DlnaPlaylistTest {
    private val control = URI("http://media.example.test:1338/ctl")

    private fun resources(vararg sizes: String?) = sizes.mapIndexed { index, size ->
        DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("res").apply {
            textContent = "video$index"
            if (size != null) setAttribute("resolution", size)
        }
    }

    @Test fun selectsLargestExistingResourceWithinPipBudget() {
        assertEquals("video2", selectDlnaResource(resources("3840x2160", "640x360", "1280x720", "1920x1080")).textContent)
    }

    @Test fun selectsSmallestKnownResourceWhenAllExceedPipBudget() {
        assertEquals("video1", selectDlnaResource(resources("3840x2160", "1920x1080", null)).textContent)
    }

    @Test fun portraitResourceUsesSamePixelDimensionsBudget() {
        assertEquals("video1", selectDlnaResource(resources("1080x1920", "720x1280", "360x640")).textContent)
    }

    @Test fun resourcesWithoutResolutionRetainServerOrder() {
        assertEquals("video0", selectDlnaResource(resources(null, null)).textContent)
    }

    @Test fun decodesObjectIdOnceAndRemovesQueryFromControlUrl() {
        assertEquals(DlnaAddress(control, "performers/42"),
            DlnaAddress.parse("upnp://media.example.test:1338/ctl?ObjectID=performers%2F42"))
        assertEquals("a%2Fb+c", DlnaAddress.parse("upnp://server/ctl?ObjectID=a%252Fb%2Bc").objectId)
    }

    @Test fun nestedContainersAndPaginatedChildrenKeepServerOrder() {
        val calls = mutableListOf<String>()
        fun video(name: String) = DlnaObject.Video(PlaylistEntry("http://server/$name.mp4", name))
        val loader = DlnaPlaylist { _, id, flag, index ->
            calls.add("$id:$flag:$index")
            when ("$id:$flag:$index") {
                "root:BrowseMetadata:0" -> DlnaPage(listOf(DlnaObject.Container("root")), 1, 1)
                "root:BrowseDirectChildren:0" -> DlnaPage(listOf(DlnaObject.Container("page1")), 1, 2)
                "page1:BrowseDirectChildren:0" -> DlnaPage(listOf(video("one"), video("two")), 2, 2)
                "root:BrowseDirectChildren:1" -> DlnaPage(listOf(video("three")), 1, 2)
                else -> error("Unexpected request")
            }
        }
        assertEquals(listOf("one", "two", "three"), loader.load("upnp://server/ctl?ObjectID=root").map { it.title })
        assertEquals(4, calls.size)
    }

    @Test fun itemUrlLoadsOneVideoWithoutBrowsingChildren() {
        val expected = PlaylistEntry("http://server/video.mp4", "Video")
        val loader = DlnaPlaylist { _, _, flag, _ ->
            assertEquals("BrowseMetadata", flag)
            DlnaPage(listOf(DlnaObject.Video(expected)), 1, 1)
        }
        assertEquals(listOf(expected), loader.load("upnp://server/ctl?ObjectID=42"))
    }

    @Test fun parsesDidlAndSelectsVideoInsteadOfAlbumArt() {
        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:u="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <container id="page/2"/><item id="1"><dc:title>A &amp; B</dc:title><u:class>object.item.videoItem</u:class>
            <res protocolInfo="http-get:*:image/jpeg:*">/cover.jpg</res><res protocolInfo="http-get:*:video/mp4:*" duration="1:02:03.5">/res?id=1&amp;token=x</res></item>
            <item id="2"><dc:title>Photo</dc:title><u:class>object.item.imageItem</u:class></item></DIDL-Lite>"""
        val escaped = didl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val response = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><BrowseResponse><Result>$escaped</Result><NumberReturned>3</NumberReturned><TotalMatches>3</TotalMatches></BrowseResponse></s:Body></s:Envelope>"""
        assertEquals(DlnaPage(listOf(DlnaObject.Container("page/2"),
            DlnaObject.Video(PlaylistEntry("http://media.example.test:1338/res?id=1&token=x", "A & B", 3723.5))), 3, 3),
            parseDlnaPage(response, control))
    }

    @Test fun soapEscapesObjectIdsAndIncludesPageOffset() {
        val body = dlnaBrowseBody("a&<b>", "BrowseDirectChildren", 100)
        assertTrue(body.contains("<ObjectID>a&amp;&lt;b&gt;</ObjectID>"))
        assertTrue(body.contains("<StartingIndex>100</StartingIndex>"))
    }
}
