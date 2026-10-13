import { test, expect, APIRequestContext, request as pwRequest } from '@playwright/test';
import { uniqueName, deleteCategoryCascade } from './utils/testData';

/**
 * Covers: "The list of tasks should support paging and filtering by
 * status or category."
 *
 * A dedicated category + fixed set of tasks is seeded once in beforeAll,
 * so every test in this file filters against the exact same known data
 * instead of the whole table (which other spec files are also writing to).
 */
test.describe('Task filtering and paging', () => {
  let api: APIRequestContext;
  let categoryId: number;
  let otherCategoryId: number;

  test.beforeAll(async () => {
    api = await pwRequest.newContext({
      baseURL: process.env.API_BASE_URL || 'http://localhost:8080',
    });

    const category = await (
      await api.post('/api/categories', { data: { name: uniqueName('FilterCategory') } })
    ).json();
    categoryId = category.id;

    const otherCategory = await (
      await api.post('/api/categories', { data: { name: uniqueName('OtherCategory') } })
    ).json();
    otherCategoryId = otherCategory.id;

    // 3 TODO + 2 IN_PROGRESS + 1 DONE in categoryId, plus 1 TODO in otherCategoryId
    const seedTasks = [
      { title: 'T1', status: 'TODO' },
      { title: 'T2', status: 'TODO' },
      { title: 'T3', status: 'TODO' },
      { title: 'T4', status: 'IN_PROGRESS' },
      { title: 'T5', status: 'IN_PROGRESS' },
      { title: 'T6', status: 'DONE' },
    ];
    for (const t of seedTasks) {
      await api.post('/api/tasks', { data: { ...t, categoryId } });
    }
    await api.post('/api/tasks', { data: { title: 'Elsewhere', status: 'TODO', categoryId: otherCategoryId } });
  });

  test.afterAll(async () => {
    await deleteCategoryCascade(api, categoryId);
    await deleteCategoryCascade(api, otherCategoryId);
    await api.dispose();
  });

  test('returns a paged envelope with content and metadata', async () => {
    const res = await api.get(`/api/tasks?categoryId=${categoryId}&page=0&size=10`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(body).toHaveProperty('totalPages');
    expect(body.totalElements).toBeGreaterThanOrEqual(6);
  });

  test('filters by status only', async () => {
    const res = await api.get(`/api/tasks?status=DONE&size=100`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.content.length).toBeGreaterThanOrEqual(1);
    expect(body.content.every((t: { status: string }) => t.status === 'DONE')).toBeTruthy();
  });

  test('filters by categoryId only', async () => {
    const res = await api.get(`/api/tasks?categoryId=${categoryId}&size=100`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.content.length).toBe(6);
    expect(body.content.every((t: { categoryId: number }) => t.categoryId === categoryId)).toBeTruthy();
  });

  test('filters by status AND categoryId combined', async () => {
    const res = await api.get(`/api/tasks?status=TODO&categoryId=${categoryId}&size=100`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.content.length).toBe(3);
    expect(
      body.content.every((t: { status: string; categoryId: number }) => t.status === 'TODO' && t.categoryId === categoryId),
    ).toBeTruthy();
  });

  test('a filter combination with no matches returns an empty page, not an error', async () => {
    const res = await api.get(`/api/tasks?status=DONE&categoryId=${otherCategoryId}`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.content).toEqual([]);
    expect(body.totalElements).toBe(0);
  });

  test('paginates results: page size is respected', async () => {
    const res = await api.get(`/api/tasks?categoryId=${categoryId}&page=0&size=2`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.content.length).toBe(2);
    expect(body.size).toBe(2);
  });

  test('paginates results: second page returns different items than the first', async () => {
    const page0 = await (await api.get(`/api/tasks?categoryId=${categoryId}&page=0&size=2`)).json();
    const page1 = await (await api.get(`/api/tasks?categoryId=${categoryId}&page=1&size=2`)).json();

    const page0Ids = page0.content.map((t: { id: number }) => t.id);
    const page1Ids = page1.content.map((t: { id: number }) => t.id);

    expect(page0Ids.some((id: number) => page1Ids.includes(id))).toBeFalsy();
  });

  test('requesting a page beyond the last page returns an empty content array', async () => {
    const res = await api.get(`/api/tasks?categoryId=${categoryId}&page=999&size=10`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.content).toEqual([]);
  });

  test('page size is capped at the server maximum instead of erroring', async () => {
    const res = await api.get(`/api/tasks?categoryId=${categoryId}&size=10000`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.size).toBeLessThanOrEqual(100);
  });

  test('an invalid (zero) page size falls back to the default instead of erroring', async () => {
    const res = await api.get(`/api/tasks?categoryId=${categoryId}&size=0`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.size).toBeGreaterThan(0);
  });

  test('results are consistently ordered by id across repeated requests', async () => {
    // Without a deterministic sort, Oracle doesn't guarantee row order, so
    // paging through results could skip or repeat rows between requests.
    const first = await (await api.get(`/api/tasks?categoryId=${categoryId}&size=100`)).json();
    const second = await (await api.get(`/api/tasks?categoryId=${categoryId}&size=100`)).json();

    const firstIds = first.content.map((t: { id: number }) => t.id);
    const secondIds = second.content.map((t: { id: number }) => t.id);
    expect(firstIds).toEqual(secondIds);
    expect(firstIds).toEqual([...firstIds].sort((a, b) => a - b));
  });
});
