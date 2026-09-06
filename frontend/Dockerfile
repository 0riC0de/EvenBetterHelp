FROM node:22-bookworm-slim AS build
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate
WORKDIR /workspace
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY frontend/package.json frontend/package.json
RUN pnpm install --frozen-lockfile
COPY frontend frontend
ARG NEXT_PUBLIC_DEMO_MODE=false
ENV NEXT_PUBLIC_DEMO_MODE=$NEXT_PUBLIC_DEMO_MODE
RUN pnpm --dir frontend run build
FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build /workspace/frontend/.next/standalone ./
COPY --from=build /workspace/frontend/.next/static ./frontend/.next/static
COPY --from=build /workspace/frontend/public ./frontend/public
USER node
EXPOSE 3000
CMD ["node", "frontend/server.js"]
