package net.greemdev.downloader.util

import meteordevelopment.starscript.Starscript
import meteordevelopment.starscript.utils.StarscriptError


class StarscriptFunctionContext(val name: String, val starscript: Starscript, val argCount: Int) {
    private var argPos = 1

    val functionName = "$name()"

    fun constrain(constraint: Constraint, customError: String? = null): StarscriptFunctionContext {
        if (!constraint.predicate(argCount))
            throw StarscriptError(customError ?: constraint.formatError(name, argCount))
        return this
    }

    fun<T> nextArg(type: ArgType<T>, customError: String? = null): T = starscript.popArg(argPos++, name, type, customError)
    fun nextBoolean(customError: String? = null) = nextArg(ArgType.Boolean, customError)
    fun nextString(customError: String? = null) = nextArg(ArgType.String, customError)
    fun nextNumber(customError: String? = null) = nextArg(ArgType.Number, customError)
}

class Constraint private constructor(private val data: Pair<Int, Any>, val predicate: (Int) -> Boolean) {
    init {
        if (data.first !in 0..3) error("Invalid Constraint type.")
    }

    companion object {
        @JvmStatic
        fun exactCount(number: Number) = Constraint(0 to number) { number == it }
        @JvmStatic
        fun atLeast(minimum: Number) = Constraint(1 to minimum) { it >= minimum.toInt() }
        @JvmStatic
        fun atMost(maximum: Number) = Constraint(2 to maximum) { it <= maximum.toInt() }
        fun within(range: IntRange) = Constraint(3 to range) { it in range }

        @JvmStatic
        fun within(min: Int, max: Int) = within(min..max)
    }

    fun formatError(functionName: String, argCount: Int) = buildString {
        val (type, comparerTo) = data
        append("$functionName() requires ")
        when (type) {
            0 -> append("argument".pluralize(comparerTo as Int))
            1 -> append("at least ${"argument".pluralize(comparerTo as Int)}")
            2 -> append("at most ${"argument".pluralize(comparerTo as Int)}")
            3 -> {
                comparerTo as IntRange
                append(comparerTo.first)
                append('-')
                append(comparerTo.last)
                append(" argument".pluralize(
                    comparerTo.sum().takeUnless { it == 1 && comparerTo.first == 0 } ?: 2, //account for the fact that 0-1 should still be considered plural
                    prefixQuantity = false
                ))
            }
        }
        append(", got $argCount.")
    }
}