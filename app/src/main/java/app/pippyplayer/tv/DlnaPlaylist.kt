package app.pippyplayer.tv

import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class DlnaAddress(val control: URI, val objectId: String) {
    companion object {
        fun parse(url: String): DlnaAddress {
            val uri = URI(url)
            val objectId = uri.rawQuery.split('&').map { it.split('=', limit = 2) }
                .first { URLDecoder.decode(it[0], "UTF-8") == "ObjectID" }[1]
            return DlnaAddress(
                URI("http", uri.userInfo, uri.host, uri.port, uri.path, null, null),
                URLDecoder.decode(objectId, "UTF-8")
            )
        }
    }
}

sealed interface DlnaObject {
    data class Container(val id: String) : DlnaObject
    data class Video(val entry: PlaylistEntry) : DlnaObject
}

data class DlnaPage(val objects: List<DlnaObject>, val returned: Int, val total: Int)

class DlnaPlaylist(
    private val browse: (URI, String, String, Int) -> DlnaPage = ::browseDlna
) {
    fun load(url: String): List<PlaylistEntry> {
        val address = DlnaAddress.parse(url)
        val metadata = browse(address.control, address.objectId, "BrowseMetadata", 0)
        return buildList {
            for (item in metadata.objects) {
                when (item) {
                    is DlnaObject.Video -> add(item.entry)
                    is DlnaObject.Container -> addAll(children(address.control, address.objectId))
                }
            }
        }
    }

    private fun children(control: URI, objectId: String): List<PlaylistEntry> = buildList {
        var start = 0
        do {
            val page = browse(control, objectId, "BrowseDirectChildren", start)
            for (item in page.objects) {
                when (item) {
                    is DlnaObject.Video -> add(item.entry)
                    is DlnaObject.Container -> addAll(children(control, item.id))
                }
            }
            start += page.returned
        } while (page.returned > 0 && (page.total == 0 || start < page.total))
    }
}

private const val CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1"
private const val DIDL = "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"

internal fun dlnaBrowseBody(objectId: String, flag: String, start: Int): String {
    val escaped = objectId.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """<?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
        <s:Body><u:Browse xmlns:u="$CONTENT_DIRECTORY">
        <ObjectID>$escaped</ObjectID><BrowseFlag>$flag</BrowseFlag><Filter>*</Filter>
        <StartingIndex>$start</StartingIndex><RequestedCount>100</RequestedCount><SortCriteria></SortCriteria>
        </u:Browse></s:Body></s:Envelope>""".trimIndent()
}

private fun browseDlna(control: URI, objectId: String, flag: String, start: Int): DlnaPage {
    val connection = control.toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
    connection.setRequestProperty("SOAPAction", "\"$CONTENT_DIRECTORY#Browse\"")
    connection.outputStream.use { it.write(dlnaBrowseBody(objectId, flag, start).toByteArray(Charsets.UTF_8)) }
    val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    connection.disconnect()
    return parseDlnaPage(response, control)
}

internal fun parseDlnaPage(response: String, control: URI): DlnaPage {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
    }
    val builder = factory.newDocumentBuilder()
    val soap = builder.parse(InputSource(StringReader(response)))
    val result = soap.getElementsByTagNameNS("*", "Result").item(0).textContent
    val returned = soap.getElementsByTagNameNS("*", "NumberReturned").item(0).textContent.toInt()
    val total = soap.getElementsByTagNameNS("*", "TotalMatches").item(0).textContent.toInt()
    val didl = builder.parse(InputSource(StringReader(result)))
    val nodes = didl.documentElement.childNodes
    val objects = buildList {
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                when (node.localName) {
                    "container" -> add(DlnaObject.Container(node.getAttribute("id")))
                    "item" -> {
                        val kind = node.getElementsByTagNameNS("urn:schemas-upnp-org:metadata-1-0/upnp/", "class").item(0).textContent
                        if (kind.startsWith("object.item.videoItem")) {
                            val resources = node.getElementsByTagNameNS(DIDL, "res")
                            val resource = selectDlnaResource((0 until resources.length).map { resources.item(it) as Element }
                                .filter {
                                    val protocol = it.getAttribute("protocolInfo").split(':', limit = 4)
                                    protocol[0] == "http-get" && !protocol[2].startsWith("image/")
                                })
                            val title = node.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title").item(0).textContent
                            val duration = resource.getAttributeNode("duration")?.value?.split(':')
                                ?.fold(0.0) { seconds, part -> seconds * 60 + part.toDouble() }
                            add(DlnaObject.Video(PlaylistEntry(control.resolve(resource.textContent.trim()).toString(), title, duration)))
                        }
                    }
                }
            }
        }
    }
    return DlnaPage(objects, returned, total)
}

/** Rank existing resources; this never requests or performs a transcode. */
internal fun selectDlnaResource(resources: List<Element>): Element = resources.map { element ->
    val dimensions = element.getAttributeNode("resolution")?.value?.split('x')?.map(String::toInt)
    val pixels = dimensions?.let { it[0].toLong() * it[1] }
    val withinPipSize = dimensions != null && dimensions.max() <= 1280 && dimensions.min() <= 720
    Triple(element, when {
        dimensions == null -> 2
        withinPipSize -> 0
        else -> 1
    }, if (withinPipSize) -pixels!! else pixels)
}.sortedWith(compareBy<Triple<Element, Int, Long?>> { it.second }.thenBy { it.third }).first().first
