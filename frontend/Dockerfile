FROM node:22-alpine AS dependencies

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci


FROM node:22-alpine AS build

WORKDIR /app

ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}

COPY --from=dependencies /app/node_modules ./node_modules
COPY . .

RUN test -n "$NEXT_PUBLIC_API_BASE_URL" \
    && npm run build


FROM node:22-alpine AS runtime

ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

RUN addgroup -S nodejs \
    && adduser -S nextjs -G nodejs

WORKDIR /app

COPY --from=build --chown=nextjs:nodejs /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs:nodejs

EXPOSE 3000

CMD ["node", "server.js"]
