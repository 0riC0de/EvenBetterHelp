# EvenBetterHelp

A mobile-first WhatsApp team inbox: **Next.js App Router + TypeScript + MUI + Zustand**, **Kotlin/Ktor + Koin**, and **PostgreSQL**.

This repository contains a runnable frontend demo, a tested helpdesk backend, and a compiled **Cobalt 0.0.10 messaging gateway** with persistent pairing, outbound text/media, inbound text/media ingestion and durable callbacks. It is **not yet a production-certified WhatsApp deployment**. Real account pairing/interoperability, a native call-media bridge, push-subscription sender, and production identity-provider integration remain deployment/integration work. Calling is disabled by the included gateway and never simulated as a real connection.

## Try the interface

Prerequisites: Node.js 22 and pnpm 11.19.0.

```sh
pnpm install --frozen-lockfile
pnpm --dir frontend run dev
```

Open http://127.0.0.1:3000. The default demo contains fictional, in-memory conversations. Choose a chat, **Assign to me**, send a reply, attach an image/PDF, record a voice note, change department/tags, escalate, or resolve. On mobile the inbox uses bottom navigation; swipe a message right to reply. Demo changes reset on reload; no messages reach WhatsApp. Camera and microphone access need HTTPS or localhost.

## Run the database-backed stack

1. Copy `.env.example` to `.env`. Replace the database password and both signing/webhook secrets with random values. Keep `CALLS_ENABLED=false` until the media gateway is verified.
2. Run `docker compose up --build -d`. Flyway applies the schema before the backend serves traffic.
3. Seed the **local development** account after migrations complete:

   ```sh
   docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d helpdesk' < ops/seed.sql
   ```

   PowerShell equivalent:

   ```powershell
   Get-Content ops/seed.sql -Raw | docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d helpdesk'
   ```

4. Run `node --env-file=.env ops/issue-dev-token.mjs`. Paste its 30-minute development token into the sign-in screen at http://localhost:8088. Tokens are stored in a SameSite=Strict, HttpOnly cookie; they are not placed in localStorage or WebSocket URLs.
5. Follow [gateway setup](docs/BRIDGE.md#run-the-included-messaging-gateway): set `COBALT_BRIDGE_URL=http://gateway:8090`, set a bridge secret, and run `docker compose --profile whatsapp up --build -d`. Explicitly pair your support account using the protected QR file. Without a connected gateway, live sends stay disabled.

The Compose deployment binds to localhost, keeps PostgreSQL and Ktor private, and uses Nginx to proxy WebSockets with cookie authentication. For production, provision HTTPS, set `APP_ORIGIN` to the exact public origin, use `COOKIE_SECURE=true`, configure trusted proxy headers, and replace the development token screen with your identity flow. **Docker is not installed in the authoring environment, so the Compose build has not been executed here.**

## The requested milestones

1. **Data model:** `backend/src/main/resources/db/migration/` contains agents, queues, conversations, messages, call sessions, routing rules, media references, agent memberships, audit records, receipt reconciliation, and an outbox. See [architecture](docs/ARCHITECTURE.md#1-data-model).
2. **Ktor implementation:** repositories own SQL and transactions, services own validation/workflows, and routes own HTTP. `ConversationRepository.claim` implements `FOR UPDATE SKIP LOCKED`. The bridge adapter uses a private, explicitly documented gateway contract.
3. **Next.js client:** responsive inbox, queue filtering, full-text backend search, optimistic message reconciliation, attachment uploads, MediaRecorder with Opus where supported, waveform display, speed controls, bottom sheets, call screen and browser WebRTC bindings. MUI theme tokens and `sx` are the single visual system; no ad-hoc `style` attributes.
4. **Clean-code verification:** ESLint/SonarJS enforces cognitive complexity ≤7 in TypeScript. Detekt uses threshold 7 (its inclusive diagnostic makes this slightly stricter). Both checks run against implementation files; there are no complexity suppressions. JSX composition and SQL strings are occasionally longer than the suggested 25 lines, while workflow operations are extracted into focused functions.

## Checks

```sh
pnpm --dir frontend run check
pnpm --dir frontend run test
pnpm --dir frontend run build
gradle -p backend test detekt
gradle -p gateway test detekt
```

Backend and gateway require JDK 21 and Gradle 9.5.1. The integration tests start real embedded PostgreSQL servers, apply the migrations, race claimants and retries, and verify ownership, receipt ordering and callback persistence. Embedded PostgreSQL must run as an unprivileged user on Linux. The gateway is pinned to the published Java 21 Cobalt 0.0.10 artifact; upstream development documentation describes a different, unpublished Java 25 API. Voice normalization additionally requires FFmpeg (included in the gateway Dockerfile).

Read [architecture and operating limits](docs/ARCHITECTURE.md), [gateway contract](docs/BRIDGE.md), and [validation results](docs/VALIDATION.md) before connecting a real support number.
