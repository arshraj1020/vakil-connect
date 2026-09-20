# Multi-stage build. NEXT_PUBLIC_API_BASE_URL is a build-time value in Next
# (it is inlined into the client bundle and into next.config.ts's CSP
# connect-src), so it must be supplied as a build arg, not a runtime env var -
# setting it only at `docker run` would leave the already-built bundle
# pointing at whatever it saw during `docker build`.
FROM node:20-slim AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}

RUN npm run build

FROM node:20-slim AS runner

WORKDIR /app
ENV NODE_ENV=production

COPY --from=builder /app/package*.json ./
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/.next ./.next

EXPOSE 3000

ENTRYPOINT ["npm", "start"]
