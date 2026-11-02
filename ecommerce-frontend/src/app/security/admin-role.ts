export function hasRealmRole(payload: unknown, requiredRole: string): boolean {
  if (typeof payload !== 'object' || payload === null || !('realm_access' in payload)) {
    return false;
  }
  const realmAccess = payload.realm_access;
  return typeof realmAccess === 'object'
    && realmAccess !== null
    && 'roles' in realmAccess
    && Array.isArray(realmAccess.roles)
    && realmAccess.roles.includes(requiredRole);
}
