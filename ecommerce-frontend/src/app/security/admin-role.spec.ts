import { describe, expect, it } from 'vitest';
import { hasRealmRole } from './admin-role';

describe('hasRealmRole', () => {
  it('recognizes the Keycloak realm role claim', () => {
    expect(hasRealmRole({ realm_access: { roles: ['offline_access', 'ADMIN'] } }, 'ADMIN')).toBe(true);
    expect(hasRealmRole({ realm_access: { roles: ['USER'] } }, 'ADMIN')).toBe(false);
    expect(hasRealmRole({ resource_access: { client: { roles: ['ADMIN'] } } }, 'ADMIN')).toBe(false);
    expect(hasRealmRole(null, 'ADMIN')).toBe(false);
  });
});
