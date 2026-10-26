package com.ayman.solosystem.ui

import androidx.compose.runtime.Composable
import com.ayman.solosystem.ui.theme.LocalArabic

/**
 * v1.2.2 number+unit formatting for RTL. Numbers (and "a / b" fractions) are wrapped in a Unicode LEFT-TO-RIGHT ISOLATE so
 * the bidi algorithm can't reorder them ("2720 / 200" bug) and units are Arabic words placed after the number (غ, كسل, مل, ل, كغ, كم).
 */
object UnitFmt {
    const val LRI = "\u2066"; const val PDI = "\u2069"; const val NBSP = "\u00A0"
    fun iso(s: String) = "$LRI$s$PDI"
    fun num(v: String, unit: String) = if (unit.isEmpty()) iso(v) else iso(v) + NBSP + unit
    fun frac(a: Any, b: Any, unit: String = "") = num("$a / $b", unit)
    fun g(ar: Boolean) = if (ar) "غ" else "g"
    fun kcal(ar: Boolean) = if (ar) "كسل" else "kcal"
    fun ml(ar: Boolean) = if (ar) "مل" else "ml"
    fun l(ar: Boolean) = if (ar) "ل" else "L"
    fun kg(ar: Boolean) = if (ar) "كغ" else "kg"
    fun lb(ar: Boolean) = if (ar) "رطل" else "lb"
    fun km(ar: Boolean) = if (ar) "كم" else "km"
}

@Composable fun uAr() = LocalArabic.current
