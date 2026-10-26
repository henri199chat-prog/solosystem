package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.ui.Nav
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Screen(title: String, nav: Nav?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nav != null && nav.stack.size > 1) {
                Box(Modifier.size(40.dp).clickable(role = Role.Button) { nav.back() }, contentAlignment = Alignment.Center) {
                    BasicText(if (LocalArabic.current) "→" else "←", style = tech(22.sp))
                }
            }
            GlowText(title, body(20.sp, FontWeight.Bold), Modifier.weight(1f))
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TextIn(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, number: Boolean = false, error: String? = null, singleLine: Boolean = true) {
    val pal = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Label(label, size = 13, color = Sys.text)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().thinBox(if (error != null) Sys.red else Sys.panelLine).padding(10.dp),
            textStyle = body(16.sp), singleLine = singleLine, cursorBrush = SolidColor(pal.secondary),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text))
        if (error != null) Label(error, color = Sys.red, size = 12)
    }
}

fun fmtDay(day: Long, ar: Boolean): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE d MMM", if (ar) Locale.forLanguageTag("ar-TN") else Locale.ENGLISH))
fun fmtNum(n: Int, ar: Boolean) = n.toString()

@Composable
fun KeyValue(k: String, v: String, color: androidx.compose.ui.graphics.Color = Sys.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Label(k, Modifier.weight(1f), size = 14)
        BasicText(v, style = body(14.sp, FontWeight.Bold, color))
    }
}

@Composable
fun Tabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    com.ayman.solosystem.ui.FlowRowCompat(Modifier.fillMaxWidth()) { options.forEachIndexed { i, o -> Chip(o, i == selected) { onSelect(i) } } }
}
