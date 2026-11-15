# DLNA / UPnP reference

Use the target server's ContentDirectory endpoint and advertised capabilities. Hostnames in examples are placeholders; supply the actual endpoint locally. Do not commit private server responses, library IDs, titles, paths, counts, credentials, or installation addresses.

## Sources and saved filters

PippyPlayer represents sources as `upnp://` URLs and uses HTTP SOAP for browsing. The app decodes ObjectID once and places it in the SOAP body. POST to the control endpoint without an ObjectID URL query. Returned relative resource URLs resolve against the control URL.

The resolver recognizes numeric `scenes/{id}` and `{collection}/{id}` paths with optional `/scenes` for performers, studios, tags, movies, and groups. Recognition in code does not establish server support for a collection. UI query parameters do not apply GraphQL filters or server-side sorting through this path.

Saved scene filters are available through [Stash GraphQL](stash-graphql-server.md). Their availability in GraphQL does not imply DLNA exposure. Browse the target ContentDirectory to discover supported containers; do not derive container IDs from saved-filter IDs.

## SOAP traversal

Use `BrowseMetadata` for the source object, then `BrowseDirectChildren` for containers. Request `Filter=*`, StartingIndex 0, RequestedCount 100, and empty SortCriteria. Increment StartingIndex by NumberReturned and recurse into returned containers, including `page/N` containers. PippyPlayer stops a container when NumberReturned is zero or the total is reached; if TotalMatches is zero it continues while results are returned.

Result contains escaped DIDL-Lite XML. Parse the SOAP response, extract Result's text, then parse that text as XML. NumberReturned/TotalMatches describe that browse result, not the final flattened video count. The complete queue is loaded before playback.

Namespaces:

- DIDL: `urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/`
- dc: `http://purl.org/dc/elements/1.1/`
- upnp: `urn:schemas-upnp-org:metadata-1-0/upnp/`
- dlna: `urn:schemas-dlna-org:metadata-1-0/`

## Read-only Python interrogation

Run where the LAN endpoint is reachable. Change object_id, flag, and start explicitly to inspect another object/page. No retries or alternate endpoints are used.

```python
import urllib.request
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

endpoint = "http://dlna.example:1338/ctl"
service = "urn:schemas-upnp-org:service:ContentDirectory:1"

def soap(action, arguments):
    body = f'<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:{action} xmlns:u="{service}">{arguments}</u:{action}></s:Body></s:Envelope>'
    request = urllib.request.Request(endpoint, body.encode(), {
        "Content-Type": "text/xml; charset=utf-8",
        "SOAPAction": f'"{service}#{action}"',
    })
    with urllib.request.urlopen(request, timeout=20) as response:
        return ET.fromstring(response.read())

object_id = "0"
flag = "BrowseDirectChildren"
start = 0
result = soap("Browse", f"<ObjectID>{escape(object_id)}</ObjectID><BrowseFlag>{flag}</BrowseFlag><Filter>*</Filter><StartingIndex>{start}</StartingIndex><RequestedCount>100</RequestedCount><SortCriteria></SortCriteria>")
print("Returned:", result.find(".//NumberReturned").text)
print("Total:", result.find(".//TotalMatches").text)
didl = ET.fromstring(result.find(".//Result").text)
for item in didl:
    print(item.tag, item.attrib)
    for field in item:
        print("  ", field.tag, field.attrib)
print(ET.tostring(soap("GetSortCapabilities", ""), encoding="unicode"))
```

For a stream check, use an actual returned video URL, send `Range: bytes=0-1023`, read at most 1,024 bytes, and close the response. A server may ignore Range and return 200; keep the read bounded rather than downloading the video.

## Application implementation

- [Resolver](../app/src/main/java/app/pippyplayer/tv/MediaSource.kt)
- [DLNA loader and parser](../app/src/main/java/app/pippyplayer/tv/DlnaPlaylist.kt)
- [Playlist metadata cache](../app/src/main/java/app/pippyplayer/tv/PlaylistCache.kt)
- [Resolver tests](../app/src/test/java/app/pippyplayer/tv/MediaSourceTest.kt)
- [DLNA tests](../app/src/test/java/app/pippyplayer/tv/DlnaPlaylistTest.kt)

The parser accepts classes beginning `object.item.videoItem`, then selects an `http-get` non-image resource. Current code ranks advertised resolutions: largest within 1280x720 (orientation-independent), then smallest known resolution, then missing resolution. It does not request transcoding.

The app caches parsed playlists in `cacheDir/playlists-v1`, keyed by SHA-256 of the resolved URL, for the configured hours. Direct SOAP queries bypass this cache and can differ from a cached app queue.
