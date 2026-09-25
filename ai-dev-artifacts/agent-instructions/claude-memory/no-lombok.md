
---
name: no-lombok
description: "User wants hand-written getters, not Lombok, in the movie booking project"
metadata:
  node_type: memory
  type: feedback
  originSessionId: d61ba290-3806-4e58-97cb-219a76ed9e8d
  modified: 2026-09-25T06:59:46.058Z
---

Keep hand-written getters on entities; don't add Lombok (or @Slf4j). The user asked for it to be removed after I introduced it (2026-09-25).

**Why:** user preference for plain Java; also, Lombok 1.18.46 couldn't download from Maven Central on this machine (TLS/PKIX error behind the corporate network).
**How to apply:** don't propose Lombok as a fix for getter boilerplate; use `LoggerFactory.getLogger(...)` for loggers. See [[dev-workflow]].
