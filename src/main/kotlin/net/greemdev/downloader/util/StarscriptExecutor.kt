package net.greemdev.downloader.util

import meteordevelopment.starscript.Script
import meteordevelopment.starscript.Section
import meteordevelopment.starscript.StandardLib
import meteordevelopment.starscript.Starscript
import meteordevelopment.starscript.compiler.Compiler
import meteordevelopment.starscript.compiler.Parser
import meteordevelopment.starscript.utils.StarscriptError
import dev.vankka.dsrvdownloader.Downloader

/**
 * Holds the reference to [Downloader]'s [Starscript] instance and executes/compiles [Script]s on it.
 */
object StarscriptExecutor {

    private val ss: Starscript = Starscript()

    @JvmStatic
    fun init() {
        StandardLib.init(ss)
        ss.defineGlobals()
    }

    private fun Starscript.defineGlobals() {
        //Put the starscript definitions here, or even delegate to a java-defined function if you don't want to deal with kotlin.
        //I'd recommend doing it here, because you can take advantage of kotlin, not to mention with how the wrapper around starscript works, you don't really need to define any types.
        //For example:
        stringFunc("camelCase", Constraint.within(1..2)) {
            //Constraint is my smart way of handling argument count & typing.
            //See types.kt for what it does.
            val string = nextString() //nextX functions handle the argument position for the error messages, and directly correlates to Starscript's popX functions for doing the same.
            // The primary advantage with this approach is the complete removal of repeated error messages in the source; and you can even provide a custom error message as the argument to these functions if you need to handle a special case.
            var delimiter = "-"
            if (argCount > 1) {
                delimiter = nextString()
            }
            string.split(delimiter) //split by delimiter
                .joinToString("") { //join each character with ""
                    //also, 'it' is the name implicitly given to the argument of a single-argument lambda.
                    it.replaceFirstChar { ch -> //replace the first character in each part with an uppercase version
                        ch.uppercase()
                    }
                }.replaceFirstChar {//replace the *very* first character in the entire string, so it's camelcase
                    it.lowercase()
                }
        }
        //as an alternative to `xFunc(String, Constraint)`, you can take advantage of context receivers and do:
        stringFunc("deleteMe") {
            constrain(exactCount(1))
            ""
        } //it's personal preference. I prefer to include the constraint in the xFunc arguments instead of the function body.
    }

    @JvmStatic
    fun compile(source: String): Script? {
        val result: Parser.Result = Parser.parse(source)

        return if (result.hasErrors()) {
            val err = StarscriptError("One or multiple exceptions occurred when compiling a Starscript.")
            result.errors.forEach {
                err.addSuppressed(IllegalArgumentException(it.toString()))
            }
            err.printStackTrace()

            null
        } else
            Compiler.compile(result)
    }

    @JvmStatic
    fun runSection(script: Script, sb: StringBuilder): Section? {
        return try {
            ss.run(script, sb)
        } catch (error: StarscriptError) {
            error.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun runSection(script: Script) =  runSection(script, StringBuilder())

    @JvmStatic
    fun run(script: Script, sb: StringBuilder) = runSection(script, sb)?.toString()

    @JvmStatic
    fun run(script: Script) = run(script, StringBuilder())
}