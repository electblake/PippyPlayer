# Known DLNA / UPnP server

Compiled 2026-11-15 from live probes in this chat on 2026-11-10 and 2026-11-13, local artifacts, and current source. DLNA was not re-probed on 2026-11-15.

## Endpoints and examples

| Purpose | Known value |
| --- | --- |
| Configured server | `upnp://192.168.1.44:1338` |
| HTTP SOAP endpoint | `http://192.168.1.44:1338/ctl` |
| Service | `urn:schemas-upnp-org:service:ContentDirectory:1` |
| Performer source | `upnp://192.168.1.44:1338/ctl?ObjectID=performers%2F5575` |
| Tag source | `upnp://192.168.1.44:1338/ctl?ObjectID=tags%2F17279` |
| Observed child containers | `performers/5575/page/1` through `/page/6` |
| Example resource | `http://192.168.1.44:1338/res?scene=846662` |
| Other observed scene IDs | `846664`, `846663`, `846656`, `846657`, `846658`, `846659` |

Successful historical requests supplied no credentials or authorization headers. This establishes access from this workstation at that time, not public access. SSDP discovery, device-description URLs, root object ID, and other control endpoints have not been established here.

`upnp://` is the app's source notation; SOAP uses HTTP. The app decodes ObjectID once and places it in the SOAP body. POST to `/ctl` without the ObjectID URL query. Returned relative resource URLs resolve against the control URL.

Known UI inputs:

- `https://stash.shoji.me/performers/5575/scenes?sortby=date`
- `https://stash.shoji.me/tags/17279/scenes?sortby=updated_at&sortdir=desc`
- Bare paths: `tags/17279/scenes`, `/tags/17279`, `performers/5575/scenes`, `/performers/5575`.

Only the URI path identifies the collection. The resolver ignores the UI hostname, query, and fragment; trims surrounding slashes; removes the terminal `/scenes`; and constructs an UPnP source using the configured server. A pasted `sortby=updated_at` does not sort DLNA by date.

Current code recognizes numeric `scenes/{id}` and `{collection}/{id}` with optional `/scenes`, for performers, studios, tags, movies, and groups. Recognition in code is not evidence that every collection exists on this server. The performer/tag examples above have live evidence.

## Live observations

On 2026-11-10, 545 performer videos across six containers were inspected. Every video had title, class, icon, album-art URI, and two resources: video and JPEG artwork. Video resources carried protocolInfo, size, bitrate, and duration. No date, added-date, or updated-date fields were present.

Example attributes from one video:

```xml
<res protocolInfo="http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0"
     size="6153677646" bitrate="9928702" duration="1:22:38">...</res>
<res protocolInfo="http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_MED">...</res>
```

The raw bitrate unit was not independently verified. Duration is hours:minutes:seconds, including fractional seconds. Observed `UpdateID` was `1`; it is not a per-video timestamp.

`GetSortCapabilities` returned exactly `dc:title`. PippyPlayer sends empty SortCriteria and sorts the flattened queue locally. Duration, size, and bitrate are available for local analysis, but the app retains URL/title/duration from DLNA, not size/bitrate/artwork/dates.

On 2026-11-13 the actual compiled `MediaSource.load` resolved tag `17279` to **1,854 videos**. A bounded read of its first returned video produced **HTTP 206 and 1,024 bytes**. This confirms source access, not full playback of every item. Counts are dated observations.

## SOAP traversal

Use `BrowseMetadata` for the source object, then `BrowseDirectChildren` for containers. Request `Filter=*`, StartingIndex 0, RequestedCount 100, and empty SortCriteria. Increment StartingIndex by NumberReturned and recurse into returned containers, including `page/N` containers. PippyPlayer stops a container when NumberReturned is zero or the total is reached; if TotalMatches is zero it continues while results are returned.

Result contains escaped DIDL-Lite XML. Parse the SOAP response, extract Result's text, then parse that text as XML. NumberReturned/TotalMatches describe that browse result, not the final flattened video count. The complete queue is loaded before playback.

Namespaces:

- DIDL: `urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/`
- dc: `http://purl.org/dc/elements/1.1/`
- upnp: `urn:schemas-upnp-org:metadata-1-0/upnp/`
- Observed dlna declaration: `urn:schemas-dlna-org:metadata-1-0/`

## Read-only Python interrogation

Run where the LAN endpoint is reachable. Change object_id, flag, and start explicitly to inspect another object/page. No retries or alternate endpoints are used.

```python
import urllib.request
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

endpoint = "http://192.168.1.44:1338/ctl"
service = "urn:schemas-upnp-org:service:ContentDirectory:1"

def soap(action, arguments):
    body = f'<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:{action} xmlns:u="{service}">{arguments}</u:{action}></s:Body></s:Envelope>'
    request = urllib.request.Request(endpoint, body.encode(), {
        "Content-Type": "text/xml; charset=utf-8",
        "SOAPAction": f'"{service}#{action}"',
    })
    with urllib.request.urlopen(request, timeout=20) as response:
        return ET.fromstring(response.read())

object_id = "tags/17279"
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

## Current code and evidence

- [Resolver](../app/src/main/java/app/pippyplayer/tv/MediaSource.kt)
- [DLNA loader and parser](../app/src/main/java/app/pippyplayer/tv/DlnaPlaylist.kt)
- [Playlist metadata cache](../app/src/main/java/app/pippyplayer/tv/PlaylistCache.kt)
- [Resolver tests](../app/src/test/java/app/pippyplayer/tv/MediaSourceTest.kt)
- [DLNA tests](../app/src/test/java/app/pippyplayer/tv/DlnaPlaylistTest.kt)

The parser accepts classes beginning `object.item.videoItem`, then selects an `http-get` non-image resource. Current code ranks advertised resolutions: largest within 1280x720 (orientation-independent), then smallest known resolution, then missing resolution. It does not request transcoding. Resolution was absent from the historical sample.

The app caches parsed playlists in `cacheDir/playlists-v1`, keyed by SHA-256 of the resolved URL, for the configured hours. Equivalent pasted URLs share a cache entry. Direct SOAP queries bypass this cache, so results can differ from a cached app queue.

Historical local artifacts, ignored by Git and not guaranteed in another checkout: `artifacts/dlna-raw.xml`, `dlna-decoded.xml`, `dlna-metadata.xml`, `dlna-page.xml`, `before-url-only.json`, `DlnaProbe.java`, and `TagDlnaProbe.java`. The decoded file still contains SOAP with escaped Result text. `dlna-test-server.py` is a synthetic fixture, not this server.

See [GraphQL](stash-graphql-server.md) for separately verified timestamps and file metadata.
