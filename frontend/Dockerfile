# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM node:22-alpine AS builder

WORKDIR /app

# Dependencies in their own layer, keyed on the manifests alone.
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund 2>/dev/null || npm install --no-audit --no-fund

COPY tsconfig.json vite.config.ts index.html ./
COPY src/ src/

# A browser bundle has no server-side configuration, so the API location is
# fixed at build time.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
# Static files only: no Node process in production, nothing to exploit but nginx.
FROM nginx:1.27-alpine AS runtime

RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/app.conf
COPY --from=builder /app/dist /usr/share/nginx/html

# The stock nginx image already ships an unprivileged user.
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx \
    && touch /tmp/nginx.pid && chown nginx:nginx /tmp/nginx.pid \
    && sed -i 's|pid .*;|pid /tmp/nginx.pid;|' /etc/nginx/nginx.conf

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    # 127.0.0.1, not "localhost": the healthcheck should test the server, not
# the container's name resolution order.
    CMD wget -q --spider http://127.0.0.1:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
