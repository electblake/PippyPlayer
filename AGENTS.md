# Project Agent Instructions

- Before working in this repository, check repository-local agent instructions and project-local skills first. Prefer `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, and `.agents/skills` in this project over user-home or global defaults.

## DLNA and Stash server references

- Before working with DLNA or Stash, read the [media server reference index](docs/media-server-reference.md) and the relevant server reference: [DLNA / UPnP](docs/dlna-server.md) or [Stash GraphQL](docs/stash-graphql-server.md).
- Use these references for known endpoints, sample IDs, dated observations, and read-only interrogation examples. For GraphQL, also consult the [sample scene query](docs/stash-scenes.graphql) and [selected schema snapshot](docs/stash-graphql-schema-2026-11-15.json). Treat historical observations as dated evidence, not guaranteed current server behavior.

## GitHub releases

- Load the [PippyPlayer release regression skill](.agents/skills/pippyplayer-release-regressions/SKILL.md) when preparing or publishing a PippyPlayer release, or investigating a published regression. After publication, complete its regression checks in the same task. The project authorizes fixing, committing, and publishing a verified patch for a confirmed regression without a separate prompt.
- Use the GitHub CLI (`gh release`) directly for all GitHub release operations, including inspecting, creating, publishing, editing, and uploading release assets.
- Do not discover, inspect, or try GitHub connector/plugin/MCP tools for release tasks. Do not spend a tool call or commentary turn checking or explaining whether the connector supports releases; `gh` is the required path from the start.
- If `gh` fails or is unavailable, stop and report the failure. Do not switch to a connector, browser, or other release provider.
