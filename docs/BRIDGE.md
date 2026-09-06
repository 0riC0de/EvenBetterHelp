# Cobalt integration boundary

The `CobaltBridgeAdapter` implements **our private HTTP gateway protocol**. Cobalt does not ship these endpoints. The `gateway/` module now implements messaging against the actual published Cobalt SDK. It compiles against that SDK and has delivery-ledger tests, but has not been paired with or tested against a live WhatsApp account. Native call-media bridging remains external work.

The upstream [Cobalt README](https://github.com/Auties00/Cobalt) describes the Java 25 development API. Direct verification on 2026-09-06 found that `cobalt-lib:0.1.0` was not available in Maven Central; the [published metadata](https://repo.maven.apache.org/maven2/com/github/auties00/cobalt/maven-metadata.xml) lists `com.github.auties00:cobalt:0.0.10`. This implementation pins 0.0.10, uses its Java 21 `it.auties.whatsapp.api.Whatsapp` classes, persistent `ControllerSerializer`, and CompletableFuture API. Source signatures were inspected from its published sources JAR and compilation verified. Do not use development-branch examples with this pinned dependency. Live protocol compatibility still requires account testing; Cobalt is unofficial and may stop interoperating with WhatsApp.

## Run the included messaging gateway

1. Configure `.env` with `COBALT_BRIDGE_URL=http://gateway:8090` and a random `COBALT_BRIDGE_TOKEN`. Keep the distinct callback token. Set S3 credentials/bucket/region and `MEDIA_ALLOWED_HOST` to the exact bucket hostname for media. Without S3, text remains supported but media ingestion/upload is unavailable.
2. Run `docker compose --profile whatsapp up --build -d`. One gateway process owns one paired account and a persistent `cobalt-session` volume. Do not run concurrent replicas against the same session directory.
3. After the gateway reports pairing is required, privately copy `/session/pairing.jpg` from its container and scan it from WhatsApp → Linked devices. For example, `docker compose cp gateway:/session/pairing.jpg ./pairing.jpg`. Treat that file as a credential and delete your local copy after pairing. The gateway removes its copy after login; the pairing keys remain in the protected volume. No account is paired by running the test suite.
4. Refresh the live inbox. Authenticated `/capabilities` reports actual connection readiness, cached for five seconds by Ktor. Calls remain false for this SDK implementation. A paired session reconnects from the persistent volume.

The gateway serializes each outbound UUID in PostgreSQL before touching the provider, uses a deterministic provider message ID, and records accepted acknowledgements. Concurrent duplicates cannot dispatch twice. A crash/timeout after dispatch is marked `uncertain`; automatic retries return conflict, rather than resend. Reconcile those entries with the provider before manually marking accepted or explicitly authorizing a new attempt. This favors avoiding duplicate customer messages over blindly retrying an ambiguous delivery. The app outbox dead-letters repeated conflicts after its retry budget. Signed download URL rotation does not change the stable payload hash; the stable `mediaId` is included.

PDF construction uses raw Cobalt builders and omits optional thumbnails/page-count extraction. Cobalt still requires its transitive Aspose runtime because its media utility class references those types; removing it caused a NoClassDefFoundError during live pairing. The gateway resolves that dependency from the vendor repository. FFmpeg normalizes voice to mono Opus at 32 kbit/s with a two-minute cap. Two concurrent sends and two inbound media downloads are allowed. Files are limited to 16 MiB; the upstream SDK still buffers decrypted media, isolated to the gateway rather than Ktor's helpdesk service. Inbound metadata size checks and bounded concurrency reduce that risk but do not turn the SDK into a streaming media API.

## Outbound messages

Ktor sends `POST {COBALT_BRIDGE_URL}/messages`, authenticated with `Authorization: Bearer {COBALT_BRIDGE_TOKEN}`, and `Idempotency-Key: <client UUID>`:

```json
{"id":"client-uuid","jid":"customer@s.whatsapp.net","kind":"text","body":"Hello!","mediaUrl":null}
```

Return `200 {"providerId":"WhatsApp-message-id"}` only after Cobalt accepts the operation. Repeated requests with the same UUID must return the original provider ID without sending again. The gateway must durably reconcile the crash window after WhatsApp accepts a send but before the gateway writes its receipt; do not claim exactly-once semantics without solving this.

For image/document/sticker/voice, `mediaUrl` is a short-lived private object download and `mediaId` is its stable UUID. The included gateway accepts only HTTPS URLs on `MEDIA_ALLOWED_HOST`, forbids redirects/userinfo/nonstandard ports, reads at most 16 MiB, normalizes voice, and invokes the pinned Cobalt media builders. Do not log signed URLs. Unsupported kinds return an error.

## Inbound events

Deliver `POST http://backend:8080/bridge/events` on a private network with `Authorization: Bearer {COBALT_WEBHOOK_TOKEN}`. Retry failed callbacks with the same `providerId`.

```json
{"type":"message","providerId":"wa-message-id","jid":"customer@s.whatsapp.net","name":"Customer","body":"I need a refund","tags":["VIP"],"region":"north"}
```

```json
{"type":"receipt","providerId":"wa-message-id","status":"read"}
```

Receipt values: sent/delivered/read. Text and image/document/sticker/voice callbacks are implemented. For media, the gateway downloads through Cobalt, stores it in private S3 under `incoming/<UUID>`, and includes `kind` plus `media: {id,objectKey,fileName,contentType,bytes}`. The backend records a customer-owned media asset before the message in one transaction. Groups/newsletters and the account's own incoming-message echoes are excluded. Callbacks are persisted in PostgreSQL and retried until acknowledged. Failures before that persistence point (such as S3 outage during initial media ingestion) currently require replay from the provider's persisted message history; automatic history replay is not implemented.

## Calling contract

Enable only after a real native-media gateway and TURN service pass interoperability tests. Ktor posts `/signals` using `{callId,type,payload}`, where payload is a JSON-encoded string. `start` includes `{jid,kind}`. The browser then sends `offer`, `answer`, `ice`, `accept`, `decline`, or `end`. Repeated signaling must be safe; the gateway maps `callId` to a provider call ID and owns provider cleanup on browser disconnect.

Gateway callbacks use `POST /bridge/calls` with the webhook bearer token:

```json
{"callId":"call-uuid","type":"incoming","conversationId":"conversation-uuid","kind":"voice"}
```

Subsequent callbacks contain `{callId,type,payload}`; accepted types are offer/answer/ice/active/end/decline/missed/failed. The payload for SDP/ICE is a JSON-encoded browser WebRTC description/candidate. Incoming callbacks require an already assigned conversation. The gateway must persist/replay the offer until the agent accepts, translate WhatsApp native media to/from its browser WebRTC peer, and implement busy/decline/missed outcomes.

**Passing SDP through Cobalt is insufficient.** Browser DTLS-SRTP/ICE and WhatsApp’s protocol need a compatible media bridge. Its actual Cobalt APIs, codecs, encryption/session ownership and native-library deployment must be implemented and verified against the pinned version.

## Configuration

Set `COBALT_BRIDGE_URL`, `COBALT_BRIDGE_TOKEN`, and a distinct `COBALT_WEBHOOK_TOKEN`. Leave calls off. A future call-capable gateway must advertise calling support at authenticated `GET /capabilities`, in addition to `CALLS_ENABLED=true`, `TURN_SHARED_SECRET`, and comma-separated `TURN_URLS`. Never expose these secrets via `NEXT_PUBLIC_*`. Dependency failures make the health probe fail closed; the included gateway always advertises `calling=false` and rejects `/signals` with HTTP 501.
