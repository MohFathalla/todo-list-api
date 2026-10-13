import { test, expect } from '@playwright/test';
import { uniqueName, createCategory, createTask, deleteCategoryCascade, futureDate, today } from './utils/testData';

/**
 * Covers the Task CRUD scenarios and the field-level rules from the brief:
 * "Each task has a title, an optional description, a status (to do, in
 * progress, or done), an optional due date that can't be in the past,
 * and the category it belongs to."
 */
test.describe('Tasks API', () => {
  // Every category created by a test (its tasks included) is removed right
  // after that test, so the suite doesn't leave test data behind in Oracle.
  const createdCategoryIds: number[] = [];

  test.afterEach(async ({ request }) => {
    while (createdCategoryIds.length > 0) {
      await deleteCategoryCascade(request, createdCategoryIds.pop()!);
    }
  });

  test('creates a task with all fields populated', async ({ request }) => {
    const category = await createCategory(request, 'FullTask');
    createdCategoryIds.push(category.id);

    const res = await request.post('/api/tasks', {
      data: {
        title: 'Write the quarterly report',
        description: 'Include revenue and churn numbers',
        status: 'IN_PROGRESS',
        dueDate: futureDate(30),
        categoryId: category.id,
      },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.status).toBe('IN_PROGRESS');
    expect(body.categoryId).toBe(category.id);
    expect(body.categoryName).toBe(category.name);
  });

  test('creates a task with only the required fields (title + categoryId)', async ({ request }) => {
    const category = await createCategory(request, 'MinimalTask');
    createdCategoryIds.push(category.id);

    const res = await request.post('/api/tasks', {
      data: { title: uniqueName('Minimal'), categoryId: category.id },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.description).toBeFalsy();
    expect(body.dueDate).toBeFalsy();
  });

  test('defaults status to TODO when status is omitted', async ({ request }) => {
    const category = await createCategory(request, 'DefaultStatus');
    createdCategoryIds.push(category.id);

    const res = await request.post('/api/tasks', {
      data: { title: uniqueName('NoStatus'), categoryId: category.id },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.status).toBe('TODO');
  });

  test('accepts a title containing SQL metacharacters and quotes', async ({ request }) => {
    // Proves input reaches the database through parameter binding
    // (Spring Data derived queries / JPQL), never through string-built SQL.
    const category = await createCategory(request, 'SqlSafety');
    createdCategoryIds.push(category.id);
    const dangerousTitle = `Robert'); DROP TABLE Task;--`;

    const res = await request.post('/api/tasks', {
      data: { title: dangerousTitle, categoryId: category.id },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.title).toBe(dangerousTitle);

    // the table must still be usable afterwards
    const listRes = await request.get(`/api/tasks?categoryId=${category.id}`);
    expect(listRes.status()).toBe(200);
  });

  test('rejects a task with a blank title (400)', async ({ request }) => {
    const category = await createCategory(request, 'BlankTitle');
    createdCategoryIds.push(category.id);

    const res = await request.post('/api/tasks', {
      data: { title: '', categoryId: category.id },
    });

    expect(res.status()).toBe(400);
  });

  test('rejects a task whose due date is in the past (400)', async ({ request }) => {
    const category = await createCategory(request, 'PastDue');
    createdCategoryIds.push(category.id);

    const res = await request.post('/api/tasks', {
      data: { title: uniqueName('OldTask'), dueDate: '2000-01-01', categoryId: category.id },
    });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.details.some((d: string) => d.toLowerCase().includes('duedate'))).toBeTruthy();
  });

  test('accepts a task whose due date is exactly today', async ({ request }) => {
    const category = await createCategory(request, 'DueToday');
    createdCategoryIds.push(category.id);

    const res = await request.post('/api/tasks', {
      data: { title: uniqueName('DueToday'), dueDate: today(), categoryId: category.id },
    });

    expect(res.status()).toBe(201);
  });

  test('rejects a task with no categoryId (400)', async ({ request }) => {
    const res = await request.post('/api/tasks', { data: { title: uniqueName('NoCategory') } });
    expect(res.status()).toBe(400);
  });

  test('returns 404 when the categoryId does not exist', async ({ request }) => {
    const res = await request.post('/api/tasks', {
      data: { title: uniqueName('GhostCategory'), categoryId: 999999999 },
    });
    expect(res.status()).toBe(404);
  });

  test('gets a single task by id', async ({ request }) => {
    const category = await createCategory(request, 'GetTask');
    createdCategoryIds.push(category.id);
    const created = await createTask(request, category.id);

    const res = await request.get(`/api/tasks/${created.id}`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.id).toBe(created.id);
  });

  test('returns 404 for a non-existent task id', async ({ request }) => {
    const res = await request.get('/api/tasks/999999999');
    expect(res.status()).toBe(404);
  });

  test('updates a task\'s title, status and due date', async ({ request }) => {
    const category = await createCategory(request, 'UpdateTask');
    createdCategoryIds.push(category.id);
    const created = await createTask(request, category.id, { status: 'TODO' });

    const res = await request.put(`/api/tasks/${created.id}`, {
      data: {
        title: 'Updated title',
        status: 'DONE',
        dueDate: futureDate(60),
        categoryId: category.id,
      },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.title).toBe('Updated title');
    expect(body.status).toBe('DONE');
  });

  test('update resets status to TODO when status is omitted, same as create', async ({ request }) => {
    // Documents the chosen PUT semantics: the request fully replaces the
    // task, so an omitted status is not "kept as-is" - it defaults to TODO,
    // identically to how create() treats a missing status.
    const category = await createCategory(request, 'UpdateDefaultStatus');
    createdCategoryIds.push(category.id);
    const created = await createTask(request, category.id, { status: 'DONE' });

    const res = await request.put(`/api/tasks/${created.id}`, {
      data: { title: created.title, categoryId: category.id },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('TODO');
  });

  test('update clears description and due date when they are omitted', async ({ request }) => {
    // PUT is a full replace: fields not sent are cleared, not left untouched.
    const category = await createCategory(request, 'UpdateClearsFields');
    createdCategoryIds.push(category.id);
    const created = await createTask(request, category.id, {
      description: 'Will be cleared',
      dueDate: futureDate(10),
    });

    const res = await request.put(`/api/tasks/${created.id}`, {
      data: { title: created.title, categoryId: category.id },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.description).toBeFalsy();
    expect(body.dueDate).toBeFalsy();
  });

  test('moves a task to a different category on update', async ({ request }) => {
    const categoryA = await createCategory(request, 'MoveFrom');
    const categoryB = await createCategory(request, 'MoveTo');
    createdCategoryIds.push(categoryA.id, categoryB.id);
    const created = await createTask(request, categoryA.id);

    const res = await request.put(`/api/tasks/${created.id}`, {
      data: { title: created.title, categoryId: categoryB.id },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.categoryId).toBe(categoryB.id);
  });

  test('returns 404 when updating a non-existent task', async ({ request }) => {
    const category = await createCategory(request, 'UpdateMissing');
    createdCategoryIds.push(category.id);

    const res = await request.put('/api/tasks/999999999', {
      data: { title: 'X', categoryId: category.id },
    });

    expect(res.status()).toBe(404);
  });

  test('deletes an existing task (204)', async ({ request }) => {
    const category = await createCategory(request, 'DeleteTask');
    createdCategoryIds.push(category.id);
    const created = await createTask(request, category.id);

    const deleteRes = await request.delete(`/api/tasks/${created.id}`);
    expect(deleteRes.status()).toBe(204);

    const getRes = await request.get(`/api/tasks/${created.id}`);
    expect(getRes.status()).toBe(404);
  });

  test('returns 404 when deleting a non-existent task', async ({ request }) => {
    const res = await request.delete('/api/tasks/999999999');
    expect(res.status()).toBe(404);
  });

  test('deleting a task allows its now-empty category to be deleted', async ({ request }) => {
    const category = await createCategory(request, 'FreedAfterDelete');
    const task = await createTask(request, category.id);

    // category has a task -> delete must be refused
    expect((await request.delete(`/api/categories/${category.id}`)).status()).toBe(409);

    await request.delete(`/api/tasks/${task.id}`);

    // category is now empty -> delete must succeed
    expect((await request.delete(`/api/categories/${category.id}`)).status()).toBe(204);
  });
});
