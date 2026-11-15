# Known Stash GraphQL server

Live verification: 2026-11-15. This is separate from [DLNA](dlna-server.md). PippyPlayer's source resolver does not fetch GraphQL metadata.

## Endpoints and access

| Endpoint | Evidence |
| --- | --- |
| `http://192.168.1.44:9999/graphql` | Verified JSON POST queries and introspection on 2026-11-15 |
| `https://stash.shoji.me` | User-provided UI origin |
| `https://stash.shoji.me/graphql` | Candidate derived from the UI origin; not tested here |
| `http://192.168.1.44:9999/scene/914570/stream` | Historical playback artifact; not re-tested on 2026-11-15 |

Successful LAN queries used `Content-Type: application/json`, without cookies, API keys, or Authorization headers. This does not establish proxy/public access. Proxy authentication and WebSocket/subscription endpoints are unknown. No credentials are stored here.

Verified version response:

```json
{"version":"v0.32.0","hash":"9e9ad972","build_time":"2026-11-02 16:44:06"}
```

Request body for this check:

```json
{"query":"query { version { version hash build_time } }","variables":{}}
```

Always inspect the full JSON response, including top-level `errors`; HTTP success alone is insufficient. The verified queries returned `data` without errors.

## Known examples and observations

- Tag UI: `https://stash.shoji.me/tags/17279/scenes?sortby=updated_at&sortdir=desc`
- Performer UI: `https://stash.shoji.me/performers/5575/scenes?sortby=date`
- DLNA counterparts: `tags/17279` and `performers/5575`.
- Tag filter `INCLUDES`, sorted `updated_at DESC`: **2,005** scenes on 2026-11-15.
- Performer filter `INCLUDES`, sorted `created_at DESC`: **545** scenes on 2026-11-15.

The historical DLNA tag count was 1,854 on 2026-11-13. Different observation dates and API/filter behavior mean these counts do not prove a defect or equivalent collection semantics.

Sample tag scene `916422` returned created_at `2026-11-13T14:46:27-05:00`, updated_at `2026-11-14T00:47:16-05:00`, and a file with duration `127.86`, size `52306764`, dimensions `368x640`, video codec `hevc`, audio codec `aac`, bit_rate `3272752`, and mod_time `2026-11-13T11:15:45-05:00`. This is a dated example, not a permanent fixture.

## Schema and filtering

Verified signatures:

```graphql
findScene(id: ID, checksum: String): Scene
findScenes(scene_filter: SceneFilterType, scene_ids: [Int!], ids: [ID!], filter: FindFilterType): FindScenesResultType!
findTag(id: ID!): Tag
findPerformer(id: ID!): Performer
version: Version!
```

FindFilterType exposes q, page, per_page, sort (String), and direction (SortDirectionEnum). Page 1/per_page 1 were tested. Increment page for subsequent results and compare returned scene counts with count. Unbounded requests were not tested. A String sort argument does not establish all accepted sort names.

Verified sort combinations: updated_at/DESC for tags and created_at/DESC for performers. Schema enum values include ASC and DESC. UI query strings are not GraphQL arguments: construct the filter explicitly.

Tags use HierarchicalMultiCriterionInput: value, required modifier, optional depth and excludes. Performers use MultiCriterionInput: value, required modifier, optional excludes. Both accepted INCLUDES with an ID array. Descendant matching through depth was not tested; do not assume it mirrors the UI or DLNA hierarchy.

## Runnable scene query

Run from the repository root. The [query file](stash-scenes.graphql) requests technical metadata without titles or filesystem paths.

```powershell
$body = @{
    query = Get-Content -Raw -Encoding utf8 docs/stash-scenes.graphql
    variables = @{
        filter = @{ page = 1; per_page = 1; sort = 'updated_at'; direction = 'DESC' }
        sceneFilter = @{ tags = @{ value = @('17279'); modifier = 'INCLUDES' } }
    }
} | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Post -Uri 'http://192.168.1.44:9999/graphql' -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 20
```

For performers, replace sceneFilter with `@{ performers = @{ value = @('5575'); modifier = 'INCLUDES' } }` and sort with `created_at`. These filters/sorts and the saved query were exercised against the LAN server.

Schema-confirmed single-scene example (this full selection was not executed):

```graphql
query {
  findScene(id: "916422") {
    id title date created_at updated_at
    files { id duration size width height frame_rate bit_rate video_codec audio_codec mod_time }
    paths { stream screenshot preview }
    sceneStreams { url mime_type label }
  }
}
```

Prefer returned stream URLs to invented endpoints. Returned URLs may contain access parameters; do not save secret-bearing URLs in documentation. A scene ID, file ID, and DLNA container ID are distinct identifiers.

Scene created_at/updated_at, file created_at/updated_at/mod_time, and content date are distinct. Do not label every date as date added. GraphQL exposes these fields; the observed DLNA responses did not.

## Introspection

The [selected schema snapshot](stash-graphql-schema-2026-11-15.json) contains query signatures, Scene, VideoFile, ScenePathsType, SceneStreamEndpoint, result/filter/criterion types, sort/modifier enums, and Version. It contains schema metadata only, not library records or credentials. It is an excerpt, not a complete schema.

```graphql
query InspectType($name: String!) {
  __type(name: $name) {
    kind name
    fields { name type { kind name ofType { kind name ofType { kind name } } } }
    inputFields { name type { kind name ofType { kind name ofType { kind name } } } }
    enumValues { name }
  }
}
```

Supply variables such as `{"name":"SceneFilterType"}` or `{"name":"VideoFile"}`. To discover root query arguments:

```graphql
query {
  __schema {
    queryType {
      name
      fields {
        name
        args { name type { kind name ofType { kind name ofType { kind name } } } }
      }
    }
  }
}
```

Scene includes title, date, created/updated timestamps, files, paths, sceneStreams, performers, tags, studio, groups, and playback metadata. VideoFile includes duration, size, dimensions, codecs, frame_rate, bit_rate, mod_time, and its own timestamps. Consult the snapshot for nullability and additional fields.

These examples are read-only. No mutations, scans, edits, deletions, or transcoding jobs were performed. Surface endpoint and GraphQL failures as returned; there are no retries or alternate providers in these examples.
