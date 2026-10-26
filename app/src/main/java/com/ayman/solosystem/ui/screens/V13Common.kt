package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*

/** No-typing number entry: [−big][−1] value [+1][+big]. The value is in the tech font, the unit label in the body font. */
@Composable
fun StepperRow(label: String, value: Int, unit: String, onChange: (Int) -> Unit, big: Int = 5, min: Int = 0, max: Int = 999) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Label(label, size = 13, color = Sys.text)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SysButton("−$big", Modifier.weight(1f).heightIn(min = 48.dp), small = true, accent = Sys.dim, sfx = Sfx.S.MINUS) { onChange((value - big).coerceIn(min, max)) }
            SysButton("−1", Modifier.weight(1f).heightIn(min = 48.dp), small = true, accent = Sys.dim, sfx = Sfx.S.MINUS) { onChange((value - 1).coerceIn(min, max)) }
            Row(Modifier.weight(1.4f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                GlowText("$value", tech(26.sp))
                if (unit.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Label(unit, size = 13) }
            }
            SysButton("+1", Modifier.weight(1f).heightIn(min = 48.dp), small = true, sfx = Sfx.S.PLUS) { onChange((value + 1).coerceIn(min, max)) }
            SysButton("+$big", Modifier.weight(1f).heightIn(min = 48.dp), small = true, sfx = Sfx.S.PLUS) { onChange((value + big).coerceIn(min, max)) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, yes: String, accent: Color = Sys.red, onYes: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        SysPanel(Modifier.fillMaxWidth(), accent = accent) {
            TitleBox(title, accent = accent)
            Spacer(Modifier.height(8.dp))
            Label(text, Modifier.fillMaxWidth(), color = Sys.text, size = 14, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton(tr("إلغاء", "Cancel"), Modifier.weight(1f), accent = Sys.dim, sfx = Sfx.S.NO) { onDismiss() }
                SysButton(yes, Modifier.weight(1f), accent = accent, sfx = Sfx.S.YES) { onYes(); onDismiss() }
            }
        }
    }
}

@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF2010308)), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
fun SafetyNote(text: String) = Label("⚠ $text", Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Sys.amber, size = 12, w = FontWeight.Medium)

fun mmss(sec: Int) = "%d:%02d".format(java.util.Locale.US, sec / 60, sec % 60)
