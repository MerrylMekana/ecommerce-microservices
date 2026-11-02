# Keycloak ADMIN role setup

The repository does not include a Keycloak realm export or provisioning configuration. Configure the existing realm manually:

1. In realm `sring-ms-security-realm`, create the realm role `ADMIN`.
2. Assign `ADMIN` only to the intended administrator account(s); do not add it as a default realm role.
3. Keep the existing `ecommerce-angular-client` OIDC client and its authorization-code flow.
4. Ensure the `roles` client scope places realm roles in the **access token** as `realm_access.roles`. The frontend and Spring resource servers read that signed access-token claim.
5. Sign in as an administrator and confirm the access token has `realm_access.roles` containing `ADMIN`. A normal account must not have it.

The API Gateway and Product/Inventory services independently validate JWTs against the existing realm issuer. The Gateway requires `ROLE_ADMIN` for product creation and stock management; Product Service and Inventory Service enforce the same rule directly. The Angular guard and navigation are usability controls only and do not replace backend authorization.
