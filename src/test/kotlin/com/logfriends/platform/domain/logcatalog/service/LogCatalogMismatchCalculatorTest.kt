package com.logfriends.platform.domain.logcatalog.service

import com.logfriends.platform.api.dto.LogCatalogFieldResponse
import com.logfriends.platform.api.dto.LogCatalogMismatchCode
import com.logfriends.platform.domain.fieldrequest.entity.FieldType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogCatalogMismatchCalculatorTest {
    private val calculator = LogCatalogMismatchCalculator()

    @Test
    fun `does not report extra fields when no LogSpec fields exist`() {
        val mismatches = calculator.calculate(
            fields = emptyList(),
            payload = mapOf(
                "cartId" to "cart-demo-001",
                "userId" to "demo-user-001"
            )
        )

        assertThat(mismatches).isEmpty()
    }

    @Test
    fun `reports extra and missing fields against registered spec fields`() {
        val fields = listOf(
            field("cartId", required = true),
            field("userId", required = true),
            field("quantity", required = true),
            field("requestedUnitPrice", required = false)
        )

        val mismatches = calculator.calculate(
            fields = fields,
            payload = mapOf(
                "cartId" to "cart-demo-001",
                "requestedUnitPrice" to 69000,
                "sourcePage" to "shop"
            )
        )

        assertThat(mismatches.map { it.code to it.fieldName })
            .containsExactly(
                LogCatalogMismatchCode.MISSING_FIELD to "quantity",
                LogCatalogMismatchCode.EXTRA_FIELD to "sourcePage",
                LogCatalogMismatchCode.MISSING_FIELD to "userId"
            )
    }

    private fun field(name: String, required: Boolean): LogCatalogFieldResponse =
        LogCatalogFieldResponse(
            name = name,
            type = FieldType.STRING,
            required = required
        )
}
