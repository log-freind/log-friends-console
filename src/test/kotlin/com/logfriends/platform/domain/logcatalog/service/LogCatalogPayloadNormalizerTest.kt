package com.logfriends.platform.domain.logcatalog.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogCatalogPayloadNormalizerTest {
    private val normalizer = LogCatalogPayloadNormalizer(jacksonObjectMapper())

    @Test
    fun `unwraps jsonb value wrapper for field comparison`() {
        val payload = mapOf(
            "type" to "jsonb",
            "value" to """
                {
                  "cartId": "cart-demo-001",
                  "userId": "demo-user-001",
                  "quantity": 1,
                  "productId": "PRD-HOM-033",
                  "sourcePage": "shop",
                  "requestedUnitPrice": 69000
                }
            """.trimIndent(),
            "null" to false
        )

        val normalized = normalizer.normalizeForFieldComparison(payload)

        assertThat(normalized.keys)
            .containsExactlyInAnyOrder(
                "cartId",
                "userId",
                "quantity",
                "productId",
                "sourcePage",
                "requestedUnitPrice"
            )
        assertThat(normalized["cartId"]).isEqualTo("cart-demo-001")
    }

    @Test
    fun `keeps raw payload when wrapper value is not a json object`() {
        val payload = mapOf(
            "type" to "jsonb",
            "value" to "not-json",
            "null" to false
        )

        val normalized = normalizer.normalizeForFieldComparison(payload)

        assertThat(normalized).isEqualTo(payload)
    }

    @Test
    fun `keeps already flat payload`() {
        val payload = mapOf(
            "cartId" to "cart-demo-001",
            "quantity" to 1
        )

        val normalized = normalizer.normalizeForFieldComparison(payload)

        assertThat(normalized).isEqualTo(payload)
    }
}
