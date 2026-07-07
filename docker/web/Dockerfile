# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS build

WORKDIR /workspace/ui-react
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"

COPY ui-react/package.json ui-react/pnpm-lock.yaml ./
RUN corepack enable && corepack prepare pnpm@10.14.0 --activate && pnpm install --frozen-lockfile

COPY ui-react/ ./
ARG NEXT_PUBLIC_API_BASE_URL=
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN pnpm run build:static

FROM nginx:1.27-alpine

COPY docker/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/ui-react/.next-build /usr/share/nginx/html

EXPOSE 80
