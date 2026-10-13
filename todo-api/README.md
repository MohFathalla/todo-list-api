# To-Do List API

A REST API for managing a to-do list, where tasks are grouped under
categories. Built with **Java 17 + Spring Boot 3 + Oracle**.

## Features

- Full CRUD for Categories and Tasks.
- A Category cannot be deleted while it still has tasks linked to it.
- Category names are unique (case-insensitive); a duplicate is rejected
  with `409 Conflict`.
- Each Task has: title (required), description (optional), status
  (TODO / IN_PROGRESS / DONE, defaults to TODO), due date (optional,
  can't be in the past), and a category (required).
- Task listing supports filtering by status and/or category, with
  pagination (capped page size, deterministic sort order).
- Consistent, clear error messages for every failure case (validation,
  not found, conflict).
- Interactive documentation via Swagger UI.

## Prerequisites

- JDK 17+
- Maven 3.9+
- Oracle Database (XE or any local instance) running on `localhost:1521`

## Database setup

A dedicated Oracle user for the project is created with the following
(run as `sysdba` if it doesn't already exist):

```sql
CREATE USER java_todo_task IDENTIFIED BY "JavaUserPassword";
GRANT CONNECT, RESOURCE TO java_todo_task;
ALTER USER java_todo_task QUOTA UNLIMITED ON USERS;
GRANT CREATE VIEW TO java_todo_task;
GRANT CREATE SEQUENCE TO java_todo_task;
ALTER USER java_todo_task ACCOUNT UNLOCK;
```

Connection details are in `src/main/resources/application.yml`:

```yaml
spring.datasource.url: jdbc:oracle:thin:@localhost:1521/orcl
spring.datasource.username: java_todo_task
spring.datasource.password: JavaUserPassword
```

> Adjust `url` if your service name differs from `orcl` (check with
> `lsnrctl status`).

Tables (`CATEGORY`, `TASK`) are created automatically on first run
(`hibernate.ddl-auto: update`).

## Running the project

```bash
mvn clean install
mvn spring-boot:run
```

The API runs at: `http://localhost:8080`

## Running the tests

```bash
mvn test
```

Runs the JUnit 5 + Mockito unit test suite for the service layer
(22 tests covering business rules such as duplicate-name rejection, the
grouped task-count query, and default-status handling).

## Swagger documentation

Once the project is running, open:

```
http://localhost:8080/swagger-ui.html
```

## Usage examples

### Create a category
```bash
curl -X POST http://localhost:8080/api/categories \
  -H "Content-Type: application/json" \
  -d '{"name": "Work"}'
```

### Create a task
```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Finish report", "status": "TODO", "dueDate": "2026-12-01", "categoryId": 1}'
```

### Filter tasks with paging
```bash
curl "http://localhost:8080/api/tasks?status=TODO&categoryId=1&page=0&size=10"
```

## Project structure

```
src/main/java/com/example/todoapi
 ├── controller/   # REST endpoints
 ├── service/      # Business logic
 ├── repository/   # Spring Data JPA repositories
 ├── entity/       # Category, Task
 ├── dto/          # Request/response objects + validation
 ├── enums/        # TaskStatus
 └── exception/    # Custom exceptions + global error handler
```
