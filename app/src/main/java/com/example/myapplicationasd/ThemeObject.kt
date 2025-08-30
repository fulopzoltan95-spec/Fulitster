package com.example.app.theme

import android.app.Activity
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.myapplicationasd.R

object ThemeManager {

    var currentTheme: ThemeOption = ThemeOption.Classic

    fun applyTheme(activity: Activity, option: ThemeOption) {
        // Háttér beállítása torzítás nélkül (layout biztosítja az arányt)
        val bg = activity.findViewById<ImageView>(R.id.bgImage)
         bg?.setImageResource(option.backgroundRes)
        val hl = activity.findViewById<ImageView>(R.id.logoImage)
        hl?.setImageResource(option.headlines)
        // Példa: elsődleges gomb átszínezése (ha van ilyen gomb)
      //  val btn = activity.findViewById<Button>(R.id.btnTheme)
     //   btn?.setBackgroundColor(ContextCompat.getColor(activity, option.primaryColorRes))
      //  btn?.setTextColor(ContextCompat.getColor(activity, option.onPrimaryColorRes))

        // Itt további UI-elemeket is átállíthatsz...
    }
}
