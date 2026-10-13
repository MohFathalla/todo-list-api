import { test, expect } from '@playwright/test';
import { uniqueName, createCategory, createTask, deleteCategoryCascade } from './utils/testData';

/**
 * Covers the Category CRUD scenarios from the project brief:
 * "let someone add, view, edit and delete ... categories"
 * and the core business rules:
 * "A category shouldn't be deleted while it still has tasks in it", and
 * category names must be unique.
 */
test.describe('Categories API', () => {
  // Every category id created in a test is tracked here and removed right
  // after that test, so the suite doesn't leave test data behind in Oracle.
  const createdCategoryIds: number[] = [];

  test.afterEach(async ({ request }) => {
    while (createdCategoryIds.length > 0) {
      await deleteCategoryCascade(request, createdCategoryIds.pop()!);
    }
  });

  test('creates a new category and returns 201 with a zero task count', async ({ request }) => {
    const res = await request.post('/api/categories', { data: { name: uniqueName('Work') } });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.id).toBeTruthy();
    expect(body.taskCount).toBe(0);
    createdCategoryIds.push(body.id);
  });

  test('rejects category creation with a blank name (400)', async ({ request }) => {
    const res = await request.post('/api/categories', { data: { name: '' } });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('Validation Failed');
    expect(body.details.some((d: string) => d.toLowerCase().includes('name'))).toBeTruthy();
  });

  test('rejects category creation with a missing name field (400)', async ({ request }) => {
    const res = await request.post('/api/categories', { data: {} });
    expect(res.status()).toBe(400);
  });

  test('rejects creating a category with a name that already exists (409)', async ({ request }) => {
    const existing = await createCategory(request, 'Duplicate');
    createdCategoryIds.push(existing.id);

    const res = await request.post('/api/categories', { data: { name: existing.name } });
    expect(res.status()).toBe(409);

    const body = await res.json();
    expect(body.error).toBe('Conflict');
  });

  test('rejects creating a category with a name that differs only in case (409)', async ({ request }) => {
    const existing = await createCategory(request, 'CaseCheck');
    createdCategoryIds.push(existing.id);

    const res = await request.post('/api/categories', { data: { name: existing.name.toUpperCase() } });
    expect(res.status()).toBe(409);
  });

  test('lists all categories including a newly created one', async ({ request }) => {
    const created = await createCategory(request, 'ListMe');
    createdCategoryIds.push(created.id);

    const res = await request.get('/api/categories');
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
    expect(body.some((c: { id: number }) => c.id === created.id)).toBeTruthy();
  });

  test('gets a single category by id', async ({ request }) => {
    const created = await createCategory(request, 'GetById');
    createdCategoryIds.push(created.id);

    const res = await request.get(`/api/categories/${created.id}`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.id).toBe(created.id);
    expect(body.name).toBe(created.name);
  });

  test('returns 404 for a non-existent category id', async ({ request }) => {
    const res = await request.get('/api/categories/999999999');

    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.error).toBe('Not Found');
  });

  test('updates an existing category name', async ({ request }) => {
    const created = await createCategory(request, 'Original');
    createdCategoryIds.push(created.id);
    const newName = uniqueName('Renamed');

    const res = await request.put(`/api/categories/${created.id}`, { data: { name: newName } });
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.name).toBe(newName);
  });

  test('allows updating a category to keep its own current name', async ({ request }) => {
    const created = await createCategory(request, 'KeepName');
    createdCategoryIds.push(created.id);

    const res = await request.put(`/api/categories/${created.id}`, { data: { name: created.name } });
    expect(res.status()).toBe(200);
  });

  test('rejects renaming a category to another category\'s existing name (409)', async ({ request }) => {
    const categoryA = await createCategory(request, 'RenameFrom');
    const categoryB = await createCategory(request, 'RenameTarget');
    createdCategoryIds.push(categoryA.id, categoryB.id);

    const res = await request.put(`/api/categories/${categoryA.id}`, { data: { name: categoryB.name } });
    expect(res.status()).toBe(409);
  });

  test('returns 404 when updating a non-existent category', async ({ request }) => {
    const res = await request.put('/api/categories/999999999', {
      data: { name: uniqueName('Ghost') },
    });
    expect(res.status()).toBe(404);
  });

  test('deletes a category that has no tasks (204)', async ({ request }) => {
    const created = await createCategory(request, 'Empty');

    const deleteRes = await request.delete(`/api/categories/${created.id}`);
    expect(deleteRes.status()).toBe(204);

    const getRes = await request.get(`/api/categories/${created.id}`);
    expect(getRes.status()).toBe(404);
  });

  test('refuses to delete a category that still has tasks (409)', async ({ request }) => {
    const category = await createCategory(request, 'Blocked');
    createdCategoryIds.push(category.id);
    await createTask(request, category.id, { title: 'Keeps the category alive' });

    const res = await request.delete(`/api/categories/${category.id}`);
    expect(res.status()).toBe(409);

    const body = await res.json();
    expect(body.error).toBe('Conflict');

    // the category must still exist after the failed delete
    const getRes = await request.get(`/api/categories/${category.id}`);
    expect(getRes.status()).toBe(200);
  });

  test('returns 404 when deleting a non-existent category', async ({ request }) => {
    const res = await request.delete('/api/categories/999999999');
    expect(res.status()).toBe(404);
  });
});
