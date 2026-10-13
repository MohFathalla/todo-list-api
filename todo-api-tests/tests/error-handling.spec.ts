import { test, expect } from '@playwright/test';
import { createCategory, createTask, deleteCategoryCascade } from './utils/testData';

/**
 * Covers: "When the input is wrong or something isn't found, the API
 * should return a clear, consistent error message."
 *
 * These tests don't re-check every endpoint (that's covered in
 * categories.spec.ts / tasks.spec.ts) - they verify the ERROR SHAPE itself
 * is uniform across every failure kind (404, 409, 400), since consistency
 * is the actual requirement in the brief.
 */
test.describe('Consistent error response shape', () => {
  const expectedKeys = ['timestamp', 'status', 'error', 'message', 'path'];
  const createdCategoryIds: number[] = [];

  test.afterEach(async ({ request }) => {
    while (createdCategoryIds.length > 0) {
      await deleteCategoryCascade(request, createdCategoryIds.pop()!);
    }
  });

  test('404 responses follow the standard error shape', async ({ request }) => {
    const res = await request.get('/api/categories/999999999');
    const body = await res.json();

    for (const key of expectedKeys) expect(body).toHaveProperty(key);
    expect(body.status).toBe(404);
    expect(body.path).toBe('/api/categories/999999999');
  });

  test('409 responses follow the standard error shape', async ({ request }) => {
    const category = await createCategory(request, 'ConflictShape');
    createdCategoryIds.push(category.id);
    await createTask(request, category.id);

    const res = await request.delete(`/api/categories/${category.id}`);
    const body = await res.json();

    for (const key of expectedKeys) expect(body).toHaveProperty(key);
    expect(body.status).toBe(409);
  });

  test('400 validation responses include a details array naming the invalid field', async ({ request }) => {
    const res = await request.post('/api/tasks', { data: { title: '' } });
    const body = await res.json();

    for (const key of expectedKeys) expect(body).toHaveProperty(key);
    expect(body.status).toBe(400);
    expect(Array.isArray(body.details)).toBeTruthy();
    expect(body.details.length).toBeGreaterThan(0);
  });

  test('an invalid enum value in the request body is rejected with 400, not 500', async ({ request }) => {
    const category = await createCategory(request, 'BadEnum');
    createdCategoryIds.push(category.id);

    const res = await request.post('/api/tasks', {
      data: { title: 'Bad status', status: 'NOT_A_REAL_STATUS', categoryId: category.id },
    });

    // Jackson fails to deserialize the enum before validation runs;
    // the handler must still turn this into a 4xx, never a raw 500.
    expect(res.status()).toBe(400);
  });

  test('an invalid enum value in a query parameter is rejected with 400, not 500', async ({ request }) => {
    // Distinct failure path from the body case above: this fails during
    // Spring's @RequestParam binding, not JSON deserialization.
    const res = await request.get('/api/tasks?status=NOT_A_REAL_STATUS');
    expect(res.status()).toBe(400);

    const body = await res.json();
    for (const key of expectedKeys) expect(body).toHaveProperty(key);
  });

  test('a malformed JSON body is rejected with 400, not 500', async ({ request }) => {
    // A plain string `data` would be JSON.stringify'd by Playwright (since
    // Content-Type is application/json), sending a *valid* JSON string
    // instead of broken JSON. Buffer.from sends the exact raw bytes, so the
    // server actually receives the malformed body this test intends to send.
    const res = await request.post('/api/categories', {
      data: Buffer.from('{ invalid json'),
      headers: { 'Content-Type': 'application/json' },
    });

    expect(res.status()).toBe(400);
  });
});
