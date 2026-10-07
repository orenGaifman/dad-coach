import { ADMIN_PHONE, BACKEND, ops, sql } from './fixtures'

/** Deletes every e2e father through the admin's own path: deactivate -> typed permanent delete. */
export default async function teardown() {
  const ids = sql(`SELECT id FROM father WHERE metadata->>'e2e' = 'true' AND phone LIKE '+1999%';`).split('\n').filter(Boolean)
  if (ids.length === 0) return
  const { loginUrl } = await ops<{ loginUrl: string }>('/api/ops/login-links', { phone: ADMIN_PHONE })
  const consume = await fetch(BACKEND + '/api/auth/consume-link', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token: loginUrl.split('#token=')[1] }),
  })
  const session = (consume.headers.getSetCookie?.() ?? []).map((c) => c.split(';')[0]).find((c) => c.startsWith('DADCOACH_SESSION='))
  const xsrf = 'e2e-teardown-xsrf'
  const headers = { Cookie: `${session}; DADCOACH_XSRF=${xsrf}`, 'X-XSRF-TOKEN': xsrf, 'Content-Type': 'application/json' }
  for (const id of ids) {
    const detail = await (await fetch(`${BACKEND}/api/admin/fathers/${id}`, { headers })).json()
    if (detail.profile?.status !== 'PAUSED') await fetch(`${BACKEND}/api/admin/fathers/${id}/deactivate`, { method: 'POST', headers })
    await fetch(`${BACKEND}/api/admin/fathers/${id}`, { method: 'DELETE', headers, body: JSON.stringify({ confirmation: detail.deleteConfirmation }) })
  }
  await fetch(BACKEND + '/api/auth/logout', { method: 'POST', headers })
  // A father who deleted himself waits for the platform's confirmation before his data is purged; the local lab
  // runs without the platform, so those rows are removed here (the platform side has nothing of theirs).
  sql(`DELETE FROM platform_person_deletion WHERE father_id IN (SELECT id FROM father WHERE metadata->>'e2e' = 'true' AND phone LIKE '+1999%');
       DELETE FROM father WHERE metadata->>'e2e' = 'true' AND phone LIKE '+1999%';`)
}
