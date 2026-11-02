import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { BehaviorSubject, firstValueFrom, Observable, of } from 'rxjs';
import { UrlTree } from '@angular/router';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  it('allows admins and redirects unauthenticated or non-admin users', async () => {
    const authentication$ = new BehaviorSubject({ isAuthenticated: true });
    const payload$ = new BehaviorSubject({ realm_access: { roles: ['ADMIN'] } });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: OidcSecurityService,
          useValue: {
            isAuthenticated$: authentication$,
            getPayloadFromAccessToken: () => payload$.asObservable()
          }
        }
      ]
    });

    const router = TestBed.inject(Router);
    const guard = () => TestBed.runInInjectionContext(() =>
      adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as Observable<boolean | UrlTree>
    );
    expect(await firstValueFrom(guard())).toBe(true);

    payload$.next({ realm_access: { roles: ['CUSTOMER'] } });
    expect(await firstValueFrom(guard())).toEqual(router.parseUrl('/'));

    authentication$.next({ isAuthenticated: false });
    expect(await firstValueFrom(guard())).toEqual(router.parseUrl('/'));
  });
});
