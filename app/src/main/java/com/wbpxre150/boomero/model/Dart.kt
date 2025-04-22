package com.wbpxre150.boomero.model

data class Dart(
    val type: DartType,
    val number: Int,
    val valid: Boolean = true,
    val scoredAsCategory: Boolean = false,
    val scoredAsCircle: Boolean = false
)

enum class DartType {
    SINGLE, DOUBLE, TRIPLE, BULLSEYE, MISS
}
