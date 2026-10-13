import { APIRequestContext } from '@playwright/test';

/**
 * Generates a collision-free name for every test run.
 * Category names are unique in the database, so tests must never hardcode
 * a fixed name that could clash between runs or parallel executions.
 */
export function uniqueName(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 100_000)}`;
}

/**
 * Returns a date `daysFromNow` days ahead, as YYYY-MM-DD.
 * Tests must never hardcode a literal future date (e.g. "2027-01-01") to
 * mean "in the future" - once real time catches up to that date, the
 * assertion silently flips from "due date in the future" to "in the past"
 * and the test starts failing for an unrelated reason.
 */
export function futureDate(daysFromNow: number): string {
  const date = new Date();
  date.setDate(date.getDate() + daysFromNow);
  return date.toISOString().split('T')[0];
}

/** Today's date as YYYY-MM-DD, in the format the API expects for dueDate. */
export function today(): string {
  return new Date().toISOString().split('T')[0];
}

export interface CategoryDto {
  id: number;
  name: string;
  taskCount: number;
}

export interface TaskDto {
  id: number;
  title: string;
  description: string | null;
  status: 'TODO' | 'IN_PROGRESS' | 'DONE';
  dueDate: string | null;
  categoryId: number;
  categoryName: string;
}

/** Creates a fresh category to isolate a test from others' data. */
export async function createCategory(
  request: APIRequestContext,
  namePrefix = 'Category',
): Promise<CategoryDto> {
  const res = await request.post('/api/categories', {
    data: { name: uniqueName(namePrefix) },
  });
  return res.json();
}

/** Creates a task under the given category, with optional field overrides. */
export async function createTask(
  request: APIRequestContext,
  categoryId: number,
  overrides: Record<string, unknown> = {},
): Promise<TaskDto> {
  const res = await request.post('/api/tasks', {
    data: { title: uniqueName('Task'), categoryId, ...overrides },
  });
  return res.json();
}

/**
 * Deletes a category and every task under it. Categories can't be deleted
 * while they still have tasks, so this removes the tasks first.
 *
 * Tests create a fresh category per run (via uniqueName), and without this
 * cleanup those categories pile up in the database forever - this is the
 * counterpart every test that calls createCategory/createTask should run
 * in an afterAll/afterEach, keeping the suite from leaving test data behind.
 */
export async function deleteCategoryCascade(request: APIRequestContext, categoryId: number): Promise<void> {
  // Large page size: test categories hold at most a handful of tasks, so one
  // page is enough and this avoids looping through pagination just to clean up.
  const res = await request.get(`/api/tasks?categoryId=${categoryId}&size=1000`);
  if (res.ok()) {
    const body = await res.json();
    for (const task of body.content ?? []) {
      await request.delete(`/api/tasks/${task.id}`);
    }
  }
  await request.delete(`/api/categories/${categoryId}`);
}
