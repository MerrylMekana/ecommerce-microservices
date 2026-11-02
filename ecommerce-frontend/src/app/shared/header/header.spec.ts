import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { HeaderComponent } from './header';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  async function createHeader(isAuthenticated: boolean, roles: string[] = []) {
    const oidc = {
      isAuthenticated$: of({ isAuthenticated }),
      userData$: of({ userData: null }),
      getPayloadFromAccessToken: vi.fn(() => of({ realm_access: { roles } })),
      authorize: vi.fn(),
      logoff: vi.fn(() => of({}))
    };

    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [provideRouter([]), { provide: OidcSecurityService, useValue: oidc }]
    }).compileComponents();
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should create', async () => {
    await createHeader(false);
    expect(component).toBeTruthy();
  });

  it('hides management links and keeps customer logout available to non-admin users', async () => {
    await createHeader(true, ['CUSTOMER']);
    expect(fixture.nativeElement.textContent).not.toContain('Catalog management');
    expect(fixture.nativeElement.textContent).not.toContain('Inventory');
    expect(fixture.nativeElement.textContent).toContain('Sign out');
  });

  it('shows management links to admins', async () => {
    await createHeader(true, ['ADMIN']);
    expect(fixture.nativeElement.textContent).toContain('Catalog management');
    expect(fixture.nativeElement.textContent).toContain('Inventory');
  });
});
