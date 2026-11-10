package com.daviddokakis.upahead

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * DEBUG ONLY: every UpAhead icon at the field's real sizes, light and dark, on the Karoo's screen, to
 * judge them at arm's length. Tap the page: the glyph inside the circle steps 1× → 1.15× → 1.3×.
 *   adb shell am start -n com.daviddokakis.upahead/.IconTestActivity
 */
class IconTestActivity : Activity() {
    private val renderer by lazy { ProfileRenderer(this) }
    private var boost = 0
    private val boosts = listOf(1f, 1.15f, 1.3f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show()
    }

    private fun show() {
        renderer.glyphBoost = boosts[boost]
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(221, 221, 221)) }
        page.addView(text("Glyph × ${boosts[boost]}  (tap to change)", Color.BLACK, 16f).apply { setPadding(16, 12, 16, 12) })
        // Field sizes: tiles 40 / 58, half page 48 / 72, full page 56 / 86 (Small / Large)
        for (px in listOf(40, 48, 56, 72, 86)) {
            for (night in listOf(false, true)) {
                val bg = if (night) Color.BLACK else Color.WHITE
                val fg = if (night) Color.WHITE else Color.BLACK
                page.addView(text("$px px  ${if (night) "dark" else "light"}", fg, 13f).apply { setBackgroundColor(bg); setPadding(16, 8, 16, 0) })
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(8, 4, 8, 10) }
                Kind.entries.chunked(maxOf(1, 460 / (px + 12))).forEach { chunk ->
                    row.addView(LinearLayout(this).apply {
                        chunk.forEach { k -> addView(ImageView(context).apply { setImageBitmap(renderer.badgeBitmap(k, px)) }, LinearLayout.LayoutParams(px + 12, px + 8)) }
                    })
                }
                page.addView(row)
            }
        }
        page.addView(text(Kind.entries.joinToString("  ") { it.name.lowercase() }, Color.DKGRAY, 11f).apply { setPadding(16, 12, 16, 24) })
        page.setOnClickListener { boost = (boost + 1) % boosts.size; show() }
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun text(s: String, color: Int, sp: Float) = TextView(this).apply { text = s; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp) }
}
