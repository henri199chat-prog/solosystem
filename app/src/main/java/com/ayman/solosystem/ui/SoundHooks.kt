package com.ayman.solosystem.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.ayman.solosystem.domain.rewards.Rarity
import com.ayman.solosystem.notify.Sfx

/** Inside ترياق الشفاء (nutrition) every tap uses the softer, calmer sound variants. */
val LocalSoftSfx = staticCompositionLocalOf { false }

/** Rarity stinger: COMMON → short shimmer, RARE → bright arpeggio, EPIC → the rare stinger a bit lower/heavier, LEGENDARY → full fanfare. */
fun playRarity(r: Rarity?) {
    when (r) {
        null, Rarity.COMMON -> Sfx.play(Sfx.S.REWARD_COMMON)
        Rarity.RARE -> Sfx.play(Sfx.S.REWARD_RARE)
        Rarity.EPIC -> Sfx.play(Sfx.S.REWARD_RARE, rate = 0.89f)
        Rarity.LEGENDARY -> Sfx.play(Sfx.S.REWARD_LEGENDARY)
    }
}

/** Tap sound resolved from context: soft variant in nutrition, secondary for dim (cancel/back) buttons. */
fun resolveTap(s: Sfx.S?, soft: Boolean, secondary: Boolean): Sfx.S? = when {
    s == null -> null
    soft && (s == Sfx.S.TAP || s == Sfx.S.TAP_SECONDARY || s == Sfx.S.SELECT) -> if (s == Sfx.S.SELECT) Sfx.S.SOFT_TOGGLE else Sfx.S.SOFT_TAP
    soft && (s == Sfx.S.TOGGLE_ON || s == Sfx.S.TOGGLE_OFF) -> Sfx.S.SOFT_TOGGLE
    soft && (s == Sfx.S.PLUS || s == Sfx.S.MINUS) -> Sfx.S.SOFT_TAP
    s == Sfx.S.TAP && secondary -> Sfx.S.TAP_SECONDARY
    else -> s
}
