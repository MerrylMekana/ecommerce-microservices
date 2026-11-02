import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { catchError, map, of, switchMap } from 'rxjs';
import { CartService } from '../../services/cart.service';
import { hasRealmRole } from '../../security/admin-role';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './header.html',
  styleUrl: './header.css'
})
export class HeaderComponent {
  private readonly oidcSecurityService = inject(OidcSecurityService);
  readonly cartService = inject(CartService);
  readonly menuOpen = signal(false);
  readonly isAuthenticated = toSignal(
    this.oidcSecurityService.isAuthenticated$.pipe(map(({ isAuthenticated }) => isAuthenticated)),
    { initialValue: false }
  );
  readonly isAdmin = toSignal(
    this.oidcSecurityService.isAuthenticated$.pipe(
      switchMap(({ isAuthenticated }) => isAuthenticated
        ? this.oidcSecurityService.getPayloadFromAccessToken().pipe(
            map(payload => hasRealmRole(payload, 'ADMIN')),
            catchError(() => of(false))
          )
        : of(false)
      )
    ),
    { initialValue: false }
  );
  readonly username = toSignal(
    this.oidcSecurityService.userData$.pipe(
      map(({ userData }) => userData?.preferred_username ?? '')
    ),
    { initialValue: '' }
  );

  login(): void {
    this.oidcSecurityService.authorize();
  }

  logout(): void {
    this.oidcSecurityService.logoff().subscribe({
      error: (error: unknown) => console.error('Unable to log out', error)
    });
  }
}
