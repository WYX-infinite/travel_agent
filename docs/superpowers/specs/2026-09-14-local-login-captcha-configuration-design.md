# Local Login Captcha Configuration Design

## Goal

Restore captcha loading for the local Vue login page while keeping Redis credentials out of source control and removing stale IntelliJ module naming.

## Scope

The change covers three related local-development problems:

1. The Vue development server does not forward `/api` requests to Spring Boot.
2. Spring Boot defaults to `127.0.0.1` even though the active Redis instance is at `192.168.154.130:6379` and requires authentication.
3. The nested IntelliJ project metadata still displays the module as `travel_agent_web`.

No captcha algorithm, authentication contract, production deployment topology, or Redis fallback implementation will change.

## Configuration Design

### Vue development proxy

`web/vue.config.js` will proxy `/api` to `http://localhost:8181`. The target will support a development-time environment override so another backend address can be used without editing source. The browser will continue requesting relative `/api/...` URLs, which avoids duplicating backend origins in components.

### Redis connection

`src/main/resources/application.yml` will retain environment-variable precedence and change the default Redis host to `192.168.154.130`. The port remains configurable and defaults to `6379`. A Redis password property will read `TRAVEL_REDIS_PASSWORD`; the password itself will never be committed.

If the password is absent or incorrect, startup or captcha access may still fail with Redis authentication errors. The developer must set `TRAVEL_REDIS_PASSWORD` in the Spring Boot run configuration.

### IntelliJ module name

The ignored local metadata under `web/.idea` will rename `travel_agent_web.iml` to `web.iml` and update `modules.xml`. Stale recent-path entries referring to the former sibling project are local history and may be removed without affecting application behavior.

## Data Flow

1. The browser loads `http://localhost:8080/login`.
2. `Login.vue` requests `/api/auth/captcha`.
3. Vue dev-server proxies the request to `http://localhost:8181/api/auth/captcha`.
4. Spring Boot generates a captcha and stores its one-time value in Redis at `192.168.154.130:6379` using the password supplied through `TRAVEL_REDIS_PASSWORD`.
5. Spring Boot returns `captchaId` and `imageDataUrl`; the frontend renders the data URL.

## Error Handling

- An unreachable backend remains visible through the existing login-page error alert.
- An unreachable or unauthenticated Redis instance remains a server-side error; no insecure in-memory fallback will be introduced.
- Secrets remain external to version-controlled configuration.

## Verification

1. Add a frontend configuration contract test that fails while the `/api` proxy is missing.
2. Add a backend configuration contract test that fails while the Redis host/password properties are missing.
3. Apply the minimal configuration changes and run the focused tests, then the full frontend and backend test suites as practical.
4. Start the frontend and backend with `TRAVEL_REDIS_PASSWORD` supplied.
5. Open `/login` and verify that the error alert disappears and a captcha image is visible.
6. Confirm the IntelliJ module label is `web` after project metadata reload.

