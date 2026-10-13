# To-Do List API — Playwright Test Suite

An automated API test suite for the **To-Do List API**, built with
[Playwright Test](https://playwright.dev/docs/api-testing) in API testing
mode (no browser) — the target project is a REST API with no UI.

## Test coverage (51 test cases)

| File | Covers | Tests |
|---|---|---|
| `tests/categories.spec.ts` | Full Category CRUD + the "no delete while it has tasks" rule + duplicate-name rejection | 15 |
| `tests/tasks.spec.ts` | Full Task CRUD + every field rule (title, dueDate, categoryId, SQL-injection safety) | 17 |
| `tests/filtering-and-paging.spec.ts` | Filtering by status/category + pagination hardening | 11 |
| `tests/error-handling.spec.ts` | Consistent error shape (404/409/400) across every failure kind | 6 |

### Scenarios covered in detail

**Categories**
- Successful creation (201) / rejects a blank or missing name (400)
- Rejects a duplicate name, exact and case-insensitive (409)
- Allows renaming a category to its own current name
- List all categories / get one by id / 404 if it doesn't exist
- Successful update / rejects renaming to another category's name (409) / 404 if missing
- Delete an empty category (204) / **refuses to delete a category with tasks (409)** / 404 if missing

**Tasks**
- Create with every field / with only required fields / without status (defaults to TODO)
- Accepts a title containing SQL metacharacters (proves parameter binding, not string-built SQL)
- Rejects a blank title (400) / rejects a due date in the past (400) / accepts a due date of today
- Rejects a missing categoryId (400) / 404 if the categoryId doesn't exist
- Get a task by id / 404 if it doesn't exist
- Update title/status/dueDate / **update resets status to TODO and clears description/dueDate when omitted** (documents the full-replace `PUT` semantics) / move a task to another category / 404 if missing
- Delete a task (204) / 404 if missing
- **Integration scenario**: deleting a category's last task frees that category for deletion

**Filtering & Paging**
- Response shape (`content`, `totalElements`, `totalPages`)
- Filter by status only / by category only / by both combined
- A filter with no matches returns an empty page (200), not an error
- Page size is respected / the second page differs from the first / a page past the end returns empty
- **Page size is capped at the server maximum** instead of erroring
- **An invalid (zero) page size falls back to the default** instead of erroring
- **Results are consistently ordered by id** across repeated requests (deterministic pagination)

**Error Handling**
- Consistent shape for every error (`timestamp`, `status`, `error`, `message`, `path`)
- Validation errors include a `details` array naming the invalid field
- An invalid enum value in the request **body** is rejected with 400, not 500
- An invalid enum value in a query **parameter** is rejected with 400, not 500
- A malformed JSON body is rejected with 400, not 500

---

## Prerequisites

1. **Node.js 18+** installed.
2. **The `todo-api` project running and connected to Oracle**, on the same
   machine or network:
   ```bash
   cd ../todo-api
   mvn spring-boot:run
   ```
   Confirm it's running at `http://localhost:8080` (or whatever address you
   set via `API_BASE_URL`).
3. The database must be running and should not hold critical data — these
   tests **write and delete real data** (categories and tasks) using
   collision-free generated names (`uniqueName`) so they won't clash with
   existing data, but running against a development/test environment
   rather than production is strongly recommended.

## Install

```bash
npm install
npx playwright install   # first time only; not required here since no browser is launched
```

## Running

### Run the whole suite
```bash
npm test
```

### Run a single file
```bash
npm run test:categories
npm run test:tasks
npx playwright test tests/filtering-and-paging.spec.ts
npx playwright test tests/error-handling.spec.ts
```

### Run a single test by name
```bash
npx playwright test -g "refuses to delete a category that still has tasks"
```

### Run with the interactive UI (useful for development/debugging)
```bash
npm run test:ui
```

### View the HTML report after a run
```bash
npm run report
```
Opens a report with every test, its result, and a trace for any failure.

### Run against a different address (e.g. staging or a different port)
```bash
API_BASE_URL=http://localhost:9090 npm test
```
(On Windows PowerShell:)
```powershell
$env:API_BASE_URL="http://localhost:9090"; npm test
```

## Important technical notes

- **`workers: 1`** in `playwright.config.ts`: the tests exercise one real
  database, so they run sequentially rather than in parallel to keep
  results deterministic and avoid flakiness — even though each test also
  uses unique data (`uniqueName`) to minimize collisions.
- **No `webServer` auto-start**: the project depends on a real Oracle
  instance, so Playwright can't start it on its own — start the API
  yourself before running the tests.
- **Every test is data-isolated and cleans up after itself**: each test
  creates the categories/tasks it needs (via `tests/utils/testData.ts`)
  and removes them in an `afterEach`/`afterAll` hook (cascading through
  any tasks first), so the suite leaves no accumulated data behind in
  Oracle and tests don't depend on run order.
- **No hardcoded calendar dates**: due dates are computed relative to
  "today" (`futureDate()` / `today()`), so a test never silently starts
  failing years later just because a fixed date moved into the past.
- **`trace: 'retain-on-failure'`**: on failure, a full trace is recorded
  so you can inspect every request/response in detail.

## Project structure

```
todo-api-tests/
 ├── playwright.config.ts
 ├── package.json
 ├── tsconfig.json
 └── tests/
     ├── utils/testData.ts         # helpers: unique categories/tasks, dates, cascading cleanup
     ├── categories.spec.ts
     ├── tasks.spec.ts
     ├── filtering-and-paging.spec.ts
     └── error-handling.spec.ts
```

## CI integration (optional)

A simple GitHub Actions example (assuming the API and Oracle are already
running in the CI environment):

```yaml
- name: Run API tests
  run: |
    cd todo-api-tests
    npm ci
    npx playwright test
  env:
    API_BASE_URL: http://localhost:8080
```
