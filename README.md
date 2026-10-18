# To-Do List API

A small REST API for managing a to-do list, where **tasks** are grouped into **categories**.
Built with Java 17, Spring Boot 3, and Oracle.

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Oracle](https://img.shields.io/badge/Database-Oracle-F80000?style=flat-square&logo=oracle&logoColor=white)
![Swagger](https://img.shields.io/badge/Docs-Swagger%20UI-85EA2D?style=flat-square&logo=swagger&logoColor=black)
![Playwright](https://img.shields.io/badge/Tests-Playwright-2EAD33?style=flat-square&logo=playwright&logoColor=white)

---

## Repository structure

```
.
├── diagrams/           # Design diagrams (this README)
├── todo-api/           # The Spring Boot REST API
└── todo-api-tests/     # Playwright API test suite (51 automated tests)
```

## Features

- Full **CRUD** for Categories and Tasks.
- 🔒 A Category **can't be deleted** while it still has Tasks.
- Task fields: title (required), description, status (`TODO` / `IN_PROGRESS` / `DONE`), due date (can't be in the past), category (required).
- Task listing supports **filtering** by status and/or category, with **paging**.
- ⚠️ Consistent, structured error responses (validation / not-found / conflict).
- Interactive API docs via **Swagger UI**.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3 (Web, Data JPA, Validation) |
| Database | Oracle |
| API docs | springdoc-openapi (Swagger UI) |
| Testing | Playwright (TypeScript) — API testing mode |

## Getting started

1. **Run the API** → see [`todo-api/README.md`](./todo-api/README.md) for the Oracle setup and how to start the server.
2. **Try it out** → open `http://localhost:8080/swagger-ui.html` once it's running.
3. **Run the tests** → see [`todo-api-tests/README.md`](./todo-api-tests/README.md) for setup, commands, and what all 51 test cases cover.

---

## Enhancements

Five change requests were scoped and designed on top of the base API (see [`enhancements/`](./enhancements/) for the full change requests, technical designs, and test cases). **Three are implemented**, two remain design-only:

| CR | Enhancement | Status |
|---|---|---|
| CR-001 | Optimistic Locking (`@Version` on Task, `409` on stale updates) | ✅ Implemented |
| CR-002 | API Idempotency (`Idempotency-Key` header on task creation) | ✅ Implemented |
| CR-003 | Scheduled Overdue-Task Detection | 📝 Designed, not implemented |
| CR-004 | Audit Trail | 📝 Designed, not implemented |
| CR-005 | AI-Assisted Task Breakdown (`POST /api/tasks/ai-breakdown`) | ✅ Implemented |

---

## Diagrams

A visual tour of the system, in order — what a client can do, how one request flows through it, how the data is shaped, and how the code is organized.

### 1️⃣ Use Case Diagram
*Every action an API client can perform, and the error each one can return.*
![Use Case Diagram](./diagrams/1.use-case-diagram.png)

### 2️⃣ Activity Diagram — Create Task
*Step by step: validation → category lookup → default status → save → response.*
![Activity Diagram](./diagrams/2.Activity_diagram.png)

### 3️⃣ ER Diagram
*How `CATEGORY` and `TASK` relate in Oracle — one category, many tasks.*
![ER Diagram](./diagrams/3.er-diagram.png)

### 4️⃣ Class Diagram
*The full picture: controllers → services → repositories → entities, plus error handling.*
![Class Diagram](./diagrams/4.class-diagram.png)

<details>
<summary><strong>Class diagram, by layer</strong> (click to expand)</summary>

**Domain model** — entities and their DTOs
![Domain Model](./diagrams/class-diagram-domain.png)

**Data layer** — services and repositories
![Data Layer](./diagrams/class-diagram-data-layer.png)

**Web layer** — controllers and centralized error handling
![Web Layer](./diagrams/class-diagram-web-layer.png)

</details>

<details>
<summary><strong>Sequence diagrams</strong> — four key requests end-to-end (click to expand)</summary>

**Create Task** — `POST /api/tasks`
![Sequence - Create Task](./diagrams/sequence-create-task.png)

**Create Category, duplicate name rejected** — `POST /api/categories`
![Sequence - Create Category Duplicate](./diagrams/sequence-create-category-duplicate.png)

**Delete Category** — `DELETE /api/categories/{id}`
![Sequence - Delete Category](./diagrams/sequence-delete-category.png)

**List Tasks, filtering & paging** — `GET /api/tasks`
![Sequence - List Tasks Paging](./diagrams/sequence-list-tasks-paging.png)

</details>

