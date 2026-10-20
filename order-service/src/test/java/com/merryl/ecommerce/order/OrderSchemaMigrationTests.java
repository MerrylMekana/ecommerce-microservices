package com.merryl.ecommerce.order;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class OrderSchemaMigrationTests {

    @Container
    private final MySQLContainer mysql = new MySQLContainer("mysql:8.3.0");

    @Test
    void migrationBackfillsLegacyOrderTotalWithoutInventingUnitPrice() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO table_orders (order_number, sku_code, price, quantity)
                    VALUES ('legacy-order', 'LEGACY-SKU', 10.00, 3)
                    """);
        }

        configuredFlyway().migrate();

        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT item.sku_code, item.quantity, item.unit_price, item.line_total
                     FROM table_order_items item
                     JOIN table_orders orders ON orders.id = item.order_id
                     WHERE orders.order_number = 'legacy-order'
                     """)) {
            org.junit.jupiter.api.Assertions.assertTrue(result.next());
            assertEquals("LEGACY-SKU", result.getString("sku_code"));
            assertEquals(3, result.getInt("quantity"));
            assertNull(result.getBigDecimal("unit_price"));
            assertEquals(new java.math.BigDecimal("10.00"), result.getBigDecimal("line_total"));
        }
    }

    @Test
    void migrationFailsRatherThanDroppingMalformedLegacyOrderData() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target(MigrationVersion.fromVersion("2"))
                .load();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO table_orders (order_number, sku_code, price, quantity)
                    VALUES ('malformed-order', 'BROKEN-SKU', 10.00, 0)
                    """);
        }

        assertThrows(RuntimeException.class, () -> configuredFlyway().migrate());

        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM table_orders WHERE order_number = 'malformed-order'
                     """)) {
            org.junit.jupiter.api.Assertions.assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    private Flyway configuredFlyway() {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .load();
    }
}
