# Validation record

Recorded during implementation on Windows with JDK 21, Gradle 9.5.1 and Node 22.

- Kotlin compilation and domain tests: passed.
- Real embedded PostgreSQL: all three helpdesk migrations, an eight-agent claim race, six concurrent send retries, cross-agent denial, inbound deduplication/media references, full-text search, early receipts and non-regressing delivery status passed. Five separate domain tests also passed.
- Included Cobalt gateway: compiles against the actual published 0.0.10 SDK. All four tests passed: SDK text construction, attachment-origin SSRF rejection, stable payload hashes across rotated download URLs, and a real PostgreSQL race/persistence test for accepted/uncertain sends and deduplicated callbacks. Detekt passes without suppressions.
- Detekt cognitive complexity check: passed with threshold 7 and no suppressions across backend and gateway.
- Frontend: TypeScript, ESLint/SonarJS complexity ≤7, all three Vitest tests, and the Next.js production build passed.
- Live WhatsApp & Cobalt integration: verified live session (972768600324:26@s.whatsapp.net). Diagnosed and resolved login query timeout; authenticated session achieves ready status. Real outbound transmission verified over live WhatsApp connection with deterministic provider IDs and ledger recording in PostgreSQL. Inbound routing, keyword rules ("refund" -> Billing & payments), and inbound deduplication verified. Outbound receipt updates (sent -> delivered -> read) verified.
- Concurrency & idempotency: verified duplicate-send protection (identical client UUID returns cached provider ID without double send; altered payload returns 409 Conflict).
- Running stack: PostgreSQL (port 5432), Ktor backend (port 8080), Cobalt gateway (port 8090), and Next.js (port 3000) running natively on Windows without Docker.

Re-run the commands in README after changing integrations or deployment configuration. This validation establishes local end-to-end integration across all four running subsystems.

