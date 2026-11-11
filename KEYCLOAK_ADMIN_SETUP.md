# Keycloak user and service-role setup

The repository does not include a Keycloak realm export or provisioning configuration. Configure the existing realm manually:

1. In realm `spring-ms-security-realm`, create the realm role `ADMIN`.
2. Assign `ADMIN` only to the intended administrator account(s); do not add it as a default realm role.
3. Keep the existing `ecommerce-angular-client` OIDC client and its authorization-code flow.
4. Ensure the `roles` client scope places realm roles in the **access token** as `realm_access.roles`. The frontend and Spring resource servers read that signed access-token claim.
5. Sign in as an administrator and confirm the access token has `realm_access.roles` containing `ADMIN`. A normal account must not have it.

## Order-to-Inventory service identity

1. Create a confidential Keycloak client, for example `order-service`, with service accounts/client-credentials enabled.
2. Create the realm role `ORDER_SERVICE` and assign it to the `order-service` service-account user only.
3. Ensure its access token contains `realm_access.roles` with `ORDER_SERVICE`.
4. Configure `ORDER_INVENTORY_CLIENT_ID`, `ORDER_INVENTORY_CLIENT_SECRET`, and `KEYCLOAK_TOKEN_URI` for Order Service. Configure `INVENTORY_URL` when Inventory is not at `http://localhost:8082`.

Order Service obtains a client-credentials access token for its Inventory HTTP calls. Inventory accepts that role only on reservation/release endpoints. Do not assign `ORDER_SERVICE` to browser users. Customer requests to Order Service still require a user JWT; product reads require an authenticated user; product creation and stock management require `ADMIN`.

## Network deployment

The checked-in Compose files start infrastructure only; they do not containerize the application services. In the current host-run setup, Product, Order, Inventory, and Notification bind to `127.0.0.1` by default, while the Gateway is the browser-facing entry point. The Gateway proxies still default to localhost. If application containers are added, set `SERVER_ADDRESS=0.0.0.0` inside each backend container, place the application services on a private Docker network, publish only the Gateway port, and set `PRODUCT_SERVICE_URL`, `ORDER_SERVICE_URL`, `INVENTORY_SERVICE_URL`, and `INVENTORY_URL` to private service DNS names. Do not publish backend service ports to the host in that deployment.

The Gateway and services validate issuer-signed JWTs and role claims. Configure audience validation at the identity-provider/API boundary before treating these services as a production security boundary; the current code does not add a custom audience validator.
