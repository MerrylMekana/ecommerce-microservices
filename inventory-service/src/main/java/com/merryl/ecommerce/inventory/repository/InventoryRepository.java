package com.merryl.ecommerce.inventory.repository;

import com.merryl.ecommerce.inventory.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    boolean existsBySkuCodeAndQuantityIsGreaterThanEqual(String skuCode, Integer quantity);

    boolean existsBySkuCode(String skuCode);

    List<Inventory> findAllByOrderBySkuCodeAsc();

    Optional<Inventory> findBySkuCode(String skuCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.skuCode = :skuCode")
    Optional<Inventory> findBySkuCodeForUpdate(@Param("skuCode") String skuCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.skuCode in :skuCodes "
            + "order by inventory.skuCode")
    List<Inventory> findAllBySkuCodeInForUpdate(@Param("skuCodes") List<String> skuCodes);

    @Modifying
    @Query("""
            update Inventory inventory
            set inventory.quantity = inventory.quantity - :quantity
            where inventory.skuCode = :skuCode
              and inventory.quantity >= :quantity
            """)
    int reserve(@Param("skuCode") String skuCode, @Param("quantity") Integer quantity);

    @Modifying
    @Query("""
            update Inventory inventory
            set inventory.quantity = inventory.quantity + :quantity
            where inventory.skuCode = :skuCode
            """)
    int release(@Param("skuCode") String skuCode, @Param("quantity") Integer quantity);
}
