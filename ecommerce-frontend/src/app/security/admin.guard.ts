import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { catchError, map, of, switchMap } from 'rxjs';
import { hasRealmRole } from './admin-role';

export const adminGuard: CanActivateFn = () => {
  const oidcSecurityService = inject(OidcSecurityService);
  const router = inject(Router);

  return oidcSecurityService.isAuthenticated$.pipe(
    switchMap(({ isAuthenticated }) => isAuthenticated
      ? oidcSecurityService.getPayloadFromAccessToken().pipe(
          map(payload => hasRealmRole(payload, 'ADMIN') || router.createUrlTree(['/'])),
          catchError(() => of(router.createUrlTree(['/'])))
        )
      : of(router.createUrlTree(['/']))
    )
  );
};
