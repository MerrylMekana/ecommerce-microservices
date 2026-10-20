package com.merryl.ecommerce.inventory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class InventorySchemaMigrationTests {

    @Container
    private final MySQLContainer mysql = new MySQLContainer("mysql:8.3.0");

    @Test
    void batchMigrationBackfillsExistingSingleSkuReservations() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target(MigrationVersion.fromVersion("3"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO table_inventory_reservations
                        (reservation_id, sku_code, quantity, request_hash, status)
                    VALUES ('legacy-reservation', 'LEGACY-SKU', 4, REPEAT('a', 64), 'RESERVED')
                    """);
        }

        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT reservation_id, sku_code, quantity
                     FROM table_inventory_reservation_items
                     WHERE reservation_id = 'legacy-reservation'
                     """)) {
            org.junit.jupiter.api.Assertions.assertTrue(result.next());
            assertEquals("legacy-reservation", result.getString("reservation_id"));
            assertEquals("LEGACY-SKU", result.getString("sku_code"));
            assertEquals(4, result.getInt("quantity"));
        }
    }
}
