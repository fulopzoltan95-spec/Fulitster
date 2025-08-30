package com.example.app.theme

import android.graphics.Paint
import androidx.annotation.DrawableRes
import com.example.myapplicationasd.R

enum class ThemeOption(
    val key: String,
    @DrawableRes val backgroundRes: Int,
    @DrawableRes val headlines: Int,
    val progressbarColor: String,
    val dialogTheme: Int
) {
    Classic(
        key = "classic",
        backgroundRes = R.drawable.bck,
        headlines =R.drawable.headlineclassic,
        progressbarColor ="#D0529C",
        dialogTheme = R.style.DialogTheme_Classic,

    ),
    Hun(
        key = "party",
        backgroundRes = R.drawable.hungarian,
        headlines =R.drawable.headlinehungarian,
        progressbarColor ="#4CD950",
        dialogTheme = R.style.DialogTheme_Party,

        ),
    Rock(
        key = "dark",
        backgroundRes = R.drawable.rock,
        headlines =R.drawable.headlinerock,
        progressbarColor ="#F96C15",
        dialogTheme = R.style.DialogTheme_Dark,


        ),
    Worn(
        key = "worn",
        backgroundRes = R.drawable.worn,
        headlines =R.drawable.headlineworn,
        progressbarColor ="#400092",
        dialogTheme = R.style.DialogTheme_Dark,
    ),
    Miami(
        key = "miami",
        backgroundRes = R.drawable.miami,
        headlines =R.drawable.headlinemiami,
        progressbarColor ="#6CDBFF",
        dialogTheme = R.style.DialogTheme_Dark,
    );

    companion object {
        fun fromKey(value: String?): ThemeOption =
            entries.find { it.key == value } ?:Classic
    }
}
