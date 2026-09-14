# Local Login Captcha Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the local Vue login page load captcha images through Spring Boot and the authenticated Redis VM, while correcting the IntelliJ module label.

**Architecture:** Keep browser API calls relative and route `/api` through the Vue development proxy. Keep Redis host, port, and password environment-overridable; use the VM address only as the local default and inject the password at runtime. Treat IntelliJ metadata as local-only configuration and do not commit it.

**Tech Stack:** Vue CLI 5, Node.js built-in test runner, Spring Boot 3.5, Spring Data Redis/Lettuce, JUnit 5, AssertJ, IntelliJ IDEA metadata.

---

## File Map

- Create `web/tests/vue-config.test.cjs`: executable contract for the Vue `/api` development proxy and its environment override.
- Modify `web/vue.config.js`: define the `/api` proxy target.
- Modify `src/test/java/com/wyx/travelagent/ConfigurationSafetyTest.java`: executable contract for Redis host, port, password indirection, and absence of the supplied password.
- Modify `src/main/resources/application.yml`: configure the Redis VM default and password environment variable.
- Create local ignored file `web/.idea/web.iml`: IntelliJ module descriptor with the desired module name.
- Modify local ignored file `web/.idea/modules.xml`: reference `web.iml`.
- Delete local ignored file `web/.idea/travel_agent_web.iml`: remove the stale module descriptor name.

### Task 1: Add the Vue API proxy

**Files:**
- Create: `web/tests/vue-config.test.cjs`
- Modify: `web/vue.config.js:1-4`

- [ ] **Step 1: Write the failing proxy contract test**

Create `web/tests/vue-config.test.cjs`:

```javascript
const assert = require('node:assert/strict')
const test = require('node:test')

const configPath = require.resolve('../vue.config.js')

function loadConfig(apiTarget) {
  const previous = process.env.VUE_APP_API_TARGET
  if (apiTarget === undefined) delete process.env.VUE_APP_API_TARGET
  else process.env.VUE_APP_API_TARGET = apiTarget
  delete require.cache[configPath]
  const config = require(configPath)
  if (previous === undefined) delete process.env.VUE_APP_API_TARGET
  else process.env.VUE_APP_API_TARGET = previous
  delete require.cache[configPath]
  return config
}

test('development server proxies API requests to the local Spring Boot port by default', () => {
  const config = loadConfig(undefined)
  assert.equal(config.devServer.proxy['/api'].target, 'http://localhost:8181')
  assert.equal(config.devServer.proxy['/api'].changeOrigin, true)
})

test('development server accepts an API target override', () => {
  const config = loadConfig('http://localhost:8182')
  assert.equal(config.devServer.proxy['/api'].target, 'http://localhost:8182')
})
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from `web`:

```powershell
node --test tests/vue-config.test.cjs
```

Expected: FAIL because `config.devServer` is undefined.

- [ ] **Step 3: Add the minimal Vue proxy configuration**

Replace `web/vue.config.js` with:

```javascript
const { defineConfig } = require('@vue/cli-service')

const apiTarget = process.env.VUE_APP_API_TARGET || 'http://localhost:8181'

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 4: Run the focused and full frontend tests**

Run from `web`:

```powershell
node --test tests/vue-config.test.cjs
npm test
```

Expected: both commands exit 0 with all tests passing.

- [ ] **Step 5: Commit the frontend proxy**

```powershell
git add web/tests/vue-config.test.cjs web/vue.config.js
git commit -m "fix: proxy frontend api requests"
```

### Task 2: Configure the authenticated Redis VM

**Files:**
- Modify: `src/test/java/com/wyx/travelagent/ConfigurationSafetyTest.java:13-33`
- Modify: `src/main/resources/application.yml:11-14`

- [ ] **Step 1: Add failing Redis configuration assertions**

Extend `committedConfigurationSupportsLocalFallbackWithoutLiteralSecrets()` with:

```java
assertThat(yaml)
    .contains("host: ${TRAVEL_REDIS_HOST:192.168.154.130}")
    .contains("port: ${TRAVEL_REDIS_PORT:6379}")
    .contains("password: ${TRAVEL_REDIS_PASSWORD:}");
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from the repository root:

```powershell
$env:JAVA_HOME='D:\program\jdk-21-temurin'
$env:Path='D:\program\jdk-21-temurin\bin;' + $env:Path
.\mvnw.cmd '-Dtest=ConfigurationSafetyTest' test
```

Expected: FAIL because the committed YAML still defaults to `127.0.0.1` and has no Redis password property.

- [ ] **Step 3: Add the minimal Redis properties**

Change the Redis block in `src/main/resources/application.yml` to:

```yaml
  data:
    redis:
      host: ${TRAVEL_REDIS_HOST:192.168.154.130}
      port: ${TRAVEL_REDIS_PORT:6379}
      password: ${TRAVEL_REDIS_PASSWORD:}
```

- [ ] **Step 4: Run the focused backend test**

```powershell
.\mvnw.cmd '-Dtest=ConfigurationSafetyTest' test
```

Expected: exit 0 with `ConfigurationSafetyTest` passing.

- [ ] **Step 5: Commit the Redis configuration**

```powershell
git add src/test/java/com/wyx/travelagent/ConfigurationSafetyTest.java src/main/resources/application.yml
git commit -m "fix: configure authenticated redis endpoint"
```

### Task 3: Correct the local IntelliJ module name

**Files:**
- Create: `web/.idea/web.iml`
- Modify: `web/.idea/modules.xml:5`
- Delete: `web/.idea/travel_agent_web.iml`

- [ ] **Step 1: Verify the stale module reference before editing**

Run:

```powershell
Select-String -LiteralPath 'web\.idea\modules.xml' -Pattern 'travel_agent_web\.iml'
```

Expected: one match on the module descriptor path.

- [ ] **Step 2: Create the renamed module descriptor**

Create `web/.idea/web.iml` with the existing descriptor content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$" />
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
  </component>
</module>
```

- [ ] **Step 3: Point IntelliJ at the renamed descriptor and remove the stale file**

Use this module entry in `web/.idea/modules.xml`:

```xml
<module fileurl="file://$PROJECT_DIR$/.idea/web.iml" filepath="$PROJECT_DIR$/.idea/web.iml" />
```

Delete `web/.idea/travel_agent_web.iml`. Do not add any `web/.idea` file to Git because the directory is intentionally ignored.

- [ ] **Step 4: Verify the local metadata**

Run:

```powershell
Select-String -LiteralPath 'web\.idea\modules.xml' -Pattern '\.idea/web\.iml'
Test-Path -LiteralPath 'web\.idea\web.iml'
Test-Path -LiteralPath 'web\.idea\travel_agent_web.iml'
git check-ignore -v web/.idea/modules.xml web/.idea/web.iml
```

Expected: the new reference matches, `web.iml` is `True`, the stale file is `False`, and both local metadata files are ignored by Git.

### Task 4: Verify the complete captcha path

**Files:**
- No committed file changes.

- [ ] **Step 1: Run the complete automated test suites**

Run from the repository root:

```powershell
$env:JAVA_HOME='D:\program\jdk-21-temurin'
$env:Path='D:\program\jdk-21-temurin\bin;' + $env:Path
.\mvnw.cmd test
Push-Location web
npm test
npm run build
Pop-Location
```

Expected: Maven tests, frontend tests, and the production frontend build all exit 0.

- [ ] **Step 2: Start an isolated backend for runtime verification**

In the execution environment only, set `TRAVEL_REDIS_PASSWORD` to the value supplied by the user. Do not echo it, save it to a file, or include it in a commit. Start Spring Boot on an unused verification port so the existing IntelliJ-managed backend is not interrupted:

```powershell
$env:SERVER_PORT='8182'
.\mvnw.cmd spring-boot:run
```

Expected: Spring Boot starts on port 8182 and connects to `192.168.154.130:6379` without Redis authentication errors.

- [ ] **Step 3: Start the frontend against the verification backend**

Stop only the diagnostic frontend process created during investigation, then run from `web`:

```powershell
$env:VUE_APP_API_TARGET='http://localhost:8182'
npm run serve
```

Expected: the Vue development server starts on `http://localhost:8080` and proxies `/api` to port 8182.

- [ ] **Step 4: Verify the API response without exposing captcha contents**

Request `http://localhost:8080/api/auth/captcha` and inspect only the HTTP status and response shape.

Expected: HTTP 200; JSON envelope reports success and contains non-empty `captchaId` and an `imageDataUrl` beginning with `data:image/png;base64,`.

- [ ] **Step 5: Verify the original browser symptom**

Open `http://localhost:8080/login` with Computer Use. Inspect the page without submitting the login form or solving the captcha.

Expected: no `Request failed with status code 404` alert, the captcha `<img>` is visible, and the login button is enabled once `captchaId` is loaded.

- [ ] **Step 6: Report the IntelliJ reload requirement**

If IntelliJ still shows the cached bracket label, reload the project or reopen the `web` directory. Confirm that the project tree displays `web [web]` or simply `web`, depending on the selected Project view.
