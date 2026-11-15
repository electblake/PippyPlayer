# Stash GraphQL reference

This reference describes schema and query patterns for general use. Schema excerpts were captured on 2026-11-15; inspect the target installation for version-specific differences. PippyPlayer's source resolver does not currently fetch GraphQL metadata.

## Access and privacy

Send JSON POST requests containing `query` and `variables` to the installation's `/graphql` endpoint, with `Content-Type: application/json` and its configured authentication. Supply the endpoint and credentials locally; do not assume anonymous access.

Inspect the full response, including top-level `errors`; HTTP success alone is insufficient. Do not save private query results, library records, saved-filter names or definitions, filesystem paths, counts, credentials, or installation addresses in documentation or fixtures. Schema metadata and generic query templates may be committed.

## Saved scene filters

The [saved-filter schema excerpt](stash-saved-filter-schema-2026-11-15.json) contains these signatures:

```graphql
findSavedFilter(id: ID!): SavedFilter
findSavedFilters(mode: FilterMode): [SavedFilter!]!
```

Use `SCENES` to list named scene filters:

```graphql
query ListSavedSceneFilters {
  findSavedFilters(mode: SCENES) {
    id
    name
    mode
  }
}
```

Retrieve a selected definition by its returned ID, supplied as a variable:

```graphql
query ReadSavedSceneFilter($id: ID!) {
  findSavedFilter(id: $id) {
    id
    name
    mode
    find_filter { q page per_page sort direction }
    object_filter
    ui_options
  }
}
```

`SavedFilter` has required `id`, `name`, and `mode` fields. Its optional `find_filter` contains search text, pagination, and sorting. `object_filter` and `ui_options` are nullable `Map` scalars, so they are selected without subfields. Introspection does not describe the internal structure of those maps.

Saved definitions can contain UI-oriented criterion data, including selected objects with IDs and labels. Do not assume `object_filter` can be passed unchanged as `SceneFilterType`. Convert each supported criterion into the corresponding input shape, preserving modifiers, hierarchy depth, exclusions, and logical combinations. Read the target schema and the relevant UI serialization before implementing this conversion. Saved-filter retrieval is verified; a general conversion and execution path has not been implemented or verified here.

`findScenes` has no saved-filter ID argument in the captured schema. Executing a saved search requires constructing `scene_filter` and `filter` from its definition. A saved-filter ID is distinct from a scene ID and a DLNA container ID.

## Relationship to DLNA

GraphQL access to saved filters does not establish that a DLNA server publishes them as browseable containers. Inspect that server's ContentDirectory separately. PippyPlayer's current DLNA resolver does not discover or execute GraphQL saved filters. Supporting them requires a GraphQL integration or explicit saved-filter containers supplied by the DLNA implementation; do not invent a DLNA ObjectID from a saved-filter ID.

## Scene queries and filtering

The [scene schema excerpt](stash-graphql-schema-2026-11-15.json) includes:

```graphql
findScene(id: ID, checksum: String): Scene
findScenes(scene_filter: SceneFilterType, scene_ids: [Int!], ids: [ID!], filter: FindFilterType): FindScenesResultType!
findTag(id: ID!): Tag
findPerformer(id: ID!): Performer
version: Version!
```

`FindFilterType` exposes `q`, `page`, `per_page`, `sort`, and `direction`. Sort direction values include `ASC` and `DESC`; a String sort argument does not establish every accepted sort name. UI query strings are not GraphQL arguments.

Tags use `HierarchicalMultiCriterionInput`: `value`, required `modifier`, optional `depth` and `excludes`. Performers use `MultiCriterionInput`: `value`, required `modifier`, optional `excludes`. Construct ID arrays from the user's selected records. Do not assume hierarchy behavior matches DLNA traversal.

The [parameterized scene query](stash-scenes.graphql) requests technical metadata. Supply `filter` and `sceneFilter` as variables. Start with bounded pagination and compare the returned scene count with `count` before requesting subsequent pages. Query results remain private even when titles and filesystem paths are omitted.

For a single scene:

```graphql
query ReadScene($id: ID!) {
  findScene(id: $id) {
    id title date created_at updated_at
    files { id duration size width height frame_rate bit_rate video_codec audio_codec mod_time }
    paths { stream screenshot preview }
    sceneStreams { url mime_type label }
  }
}
```

Use returned stream URLs rather than invented endpoints. URLs may contain access parameters and must not be saved in documentation. Scene creation/update timestamps, file creation/update/modification timestamps, and content date have distinct meanings.

## Introspection

Both linked snapshots contain schema metadata only and are excerpts, not complete schemas. Inspect a type with:

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

Useful names include `SavedFilter`, `SavedFindFilterType`, `FilterMode`, `SceneFilterType`, and `VideoFile`. These examples are read-only; they do not mutate filters or library records.
