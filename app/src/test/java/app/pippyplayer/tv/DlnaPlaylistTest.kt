package app.pippyplayer.tv

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class DlnaPlaylistTest {
    private val control = URI("http://192.168.1.44:1338/ctl")

    @Test fun decodesObjectIdOnceAndRemovesQueryFromControlUrl() {
        assertEquals(DlnaAddress(control, "performers/5575"),
            DlnaAddress.parse("upnp://192.168.1.44:1338/ctl?ObjectID=performers%2F5575"))
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
            DlnaObject.Video(PlaylistEntry("http://192.168.1.44:1338/res?id=1&token=x", "A & B", 3723.5))), 3, 3),
            parseDlnaPage(response, control))
    }

    @Test fun soapEscapesObjectIdsAndIncludesPageOffset() {
        val body = dlnaBrowseBody("a&<b>", "BrowseDirectChildren", 100)
        assertTrue(body.contains("<ObjectID>a&amp;&lt;b&gt;</ObjectID>"))
        assertTrue(body.contains("<StartingIndex>100</StartingIndex>"))
    }
}
