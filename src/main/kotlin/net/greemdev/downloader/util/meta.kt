@file:JvmName("KUtil")

package net.greemdev.downloader.util

@JvmOverloads
fun String.pluralize(quantity: Number, useES: Boolean = false, prefixQuantity: Boolean = true) =
    if (quantity != 1) buildString {
        if (prefixQuantity) append("$quantity ")
        append(this@pluralize)
        if (useES) append('e')
        append('s')
    } else {
        if (prefixQuantity) "$quantity $this"
        else this
    }