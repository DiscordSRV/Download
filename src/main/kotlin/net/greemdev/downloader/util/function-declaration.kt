@file:JvmName("Functions")
package net.greemdev.downloader.util

import meteordevelopment.starscript.Starscript
import meteordevelopment.starscript.value.Value
import meteordevelopment.starscript.value.ValueMap

typealias ConstrainedStarscriptFunction<T> = context(Constraint.Companion) StarscriptFunctionContext.() -> T
typealias StarscriptFunction<T> = StarscriptFunctionContext.() -> T

fun Starscript.func(name: String, logic: ConstrainedStarscriptFunction<Value>): Starscript {
    defineFunction(name) { ss, argCount ->
        logic(Constraint, StarscriptFunctionContext(name, ss, argCount))
    }
    return this
}

fun ValueMap.func(name: String, logic: ConstrainedStarscriptFunction<Value>): ValueMap {
    defineFunction(name) { ss, argCount ->
        logic(Constraint, StarscriptFunctionContext(name, ss, argCount))
    }
    return this
}

fun Starscript.func(name: String, constraint: Constraint, logic: StarscriptFunction<Value>): Starscript {
    defineFunction(name) { ss, argCount ->
        StarscriptFunctionContext(name, ss, argCount).constrain(constraint).logic()
    }
    return this
}

fun ValueMap.func(name: String, constraint: Constraint, logic: StarscriptFunction<Value>): ValueMap {
    defineFunction(name) { ss, argCount ->
        StarscriptFunctionContext(name, ss, argCount).constrain(constraint).logic()
    }
    return this
}

fun Starscript.booleanFunc(name: String, logic: ConstrainedStarscriptFunction<Boolean?>) =
    func(name) { BooleanValue(logic(Constraint, this)) }

fun ValueMap.booleanFunc(name: String, logic: ConstrainedStarscriptFunction<Boolean?>) =
    func(name) { BooleanValue(logic(Constraint, this)) }

fun Starscript.booleanFunc(name: String, constraint: Constraint, logic: StarscriptFunction<Boolean?>) =
    func(name, constraint) { BooleanValue(logic()) }

fun ValueMap.booleanFunc(name: String, constraint: Constraint, logic: StarscriptFunction<Boolean?>) =
    func(name, constraint) { BooleanValue(logic()) }


fun Starscript.numberFunc(name: String, logic: ConstrainedStarscriptFunction<Number?>) =
    func(name) { NumberValue(logic(Constraint, this)) }

fun ValueMap.numberFunc(name: String, logic: ConstrainedStarscriptFunction<Number?>) =
    func(name) { NumberValue(logic(Constraint, this)) }

fun Starscript.numberFunc(name: String, constraint: Constraint, logic: StarscriptFunction<Number?>) =
    func(name, constraint) { NumberValue(logic()) }

fun ValueMap.numberFunc(name: String, constraint: Constraint, logic: StarscriptFunction<Number?>) =
    func(name, constraint) { NumberValue(logic()) }


fun Starscript.stringFunc(name: String, logic: ConstrainedStarscriptFunction<String?>) =
    func(name) { StringValue(logic(Constraint, this)) }

fun ValueMap.stringFunc(name: String, logic: ConstrainedStarscriptFunction<String?>) =
    func(name) { StringValue(logic(Constraint, this)) }

fun Starscript.stringFunc(name: String, constraint: Constraint, logic: StarscriptFunction<String?>) =
    func(name, constraint) { StringValue(logic()) }

fun ValueMap.stringFunc(name: String, constraint: Constraint, logic: StarscriptFunction<String?>) =
    func(name, constraint) { StringValue(logic()) }


fun Starscript.objectFunc(name: String, logic: ConstrainedStarscriptFunction<ValueMap?>) =
    func(name) { ObjectValue(logic(Constraint, this)) }

fun ValueMap.objectFunc(name: String, logic: ConstrainedStarscriptFunction<ValueMap?>) =
    func(name) { ObjectValue(logic(Constraint, this)) }

fun Starscript.objectFunc(name: String, constraint: Constraint, logic: StarscriptFunction<ValueMap?>) =
    func(name, constraint) { ObjectValue(logic()) }

fun ValueMap.objectFunc(name: String, constraint: Constraint, logic: StarscriptFunction<ValueMap?>) =
    func(name, constraint) { ObjectValue(logic()) }


fun Starscript.mapFunc(name: String, logic: ConstrainedStarscriptFunction<Map<String, Value>?>) =
    func(name) { MapValue(logic(Constraint, this)) }

fun ValueMap.mapFunc(name: String, logic: ConstrainedStarscriptFunction<Map<String, Value>?>) =
    func(name) { MapValue(logic(Constraint, this)) }

fun Starscript.mapFunc(name: String, constraint: Constraint, logic: StarscriptFunction<Map<String, Value>?>) =
    func(name, constraint) { MapValue(logic()) }

fun ValueMap.mapFunc(name: String, constraint: Constraint, logic: StarscriptFunction<Map<String, Value>?>) =
    func(name, constraint) { MapValue(logic()) }