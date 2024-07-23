package com.sensetime.sensecode.jetbrains.raccoon.llm.tokens


internal object RaccoonTokenUtils {
    fun estimateTokensNumber(src: String): Int {
        var result: Float = 0.0F
        for (c in src) {
            result += 2F;
//            result += 1.0F;
//            result += when {
//                c.isLetter() -> 0.5F
//                c.isDigit() -> 0.25F
//                c.isWhitespace() -> 0.25F
//                c.code in 0..127 -> 0.5F
//                else -> 0.7F
//            }
        }
        return result.toInt()
    }
}
