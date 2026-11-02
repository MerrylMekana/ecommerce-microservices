package com.merryl.ecommerce.product.config;

import com.merryl.ecommerce.product.model.Product;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Collation;

import java.util.Locale;

@Configuration
public class ProductIndexConfiguration {

    @Bean
    ApplicationRunner ensureProductSkuUniqueIndex(MongoTemplate mongoTemplate) {
        return args -> mongoTemplate.indexOps(Product.class).ensureIndex(
                new Index()
                        .on("skuCode", Sort.Direction.ASC)
                        .unique()
                        .named("uq_product_sku_code")
                        .partial(PartialIndexFilter.of(
                                new Document("skuCode", new Document("$type", "string"))))
                        .collation(Collation.of(Locale.ENGLISH)
                                .strength(Collation.ComparisonLevel.secondary())));
    }
}
