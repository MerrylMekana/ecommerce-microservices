import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';

import {
  provideAuth,
  withAppInitializerAuthCheck
} from 'angular-auth-oidc-client';

import { routes } from './app.routes';
import { authInterceptor } from './interceptor/auth-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [

    provideRouter(routes),

    provideHttpClient(
      withInterceptors([
        authInterceptor
      ])
    ),

    provideAuth(
      {
        config: {
          authority: 'http://localhost:8181/realms/sring-ms-security-realm',
          redirectUrl: window.location.origin,
          postLogoutRedirectUri: window.location.origin,
          clientId: 'ecommerce-angular-client',
          scope: 'openid profile email',
          responseType: 'code'
        }
      },
      withAppInitializerAuthCheck()
    )
  ]
};