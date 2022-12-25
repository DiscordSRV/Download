@file:JvmName("Arguments")
package net.greemdev.downloader.util

import meteordevelopment.starscript.Starscript

@JvmName("pop")
@JvmOverloads
fun <T> Starscript.popArg(argPos: Int, functionName: String, type: ArgType<T>, customError: String? = null) =
    type.popper(this, customError ?: "Argument $argPos of $functionName() needs to be a ${type.friendly}.")

//sealed classes are classes whose subtypes are all known at compile-type, aka, they're classes you basically can't create anonymous versions of.
sealed class ArgType<T>(
    val friendly: kotlin.String,
    val popper: Starscript.(kotlin.String) -> T
) {
    object Boolean : ArgType<kotlin.Boolean>(
        "boolean (true/false)",
        Starscript::popBool
    )

    object String : ArgType<kotlin.String>(
        "string",
        Starscript::popString
    )

    object Number : ArgType<Double>(
        "number",
        Starscript::popNumber
    )
}