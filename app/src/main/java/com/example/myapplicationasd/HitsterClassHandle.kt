package com.example.myapplicationasd

import android.content.Context
import android.widget.Toast


data class Songinfo(val artist: String, val song: String)
class HitsterClassHandle {


        fun isHitsterLink(url: String): Boolean {
            val regex = Regex("""^(http://|https://)?(www\.hitstergame|app\.hitsternordics)\.com/.+""")
            return regex.matches(url)
        }
//hello
    fun getSpotyURL(context: Context,hitsterUrl:String):Songinfo
    {
        val parsed = parseHitsterUrl(hitsterUrl)
        var songinfo = Songinfo("","")
        if (parsed != null) {
            songinfo = getArtistAndTrack(context, parsed.lang, parsed.id)
            if (songinfo != null) {
                return songinfo;
            } else {
                Toast.makeText(context, "Nem található zene a CSV-ben", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Érvénytelen Hitster URL", Toast.LENGTH_LONG).show()
        }
        return songinfo
    }

    data class HitsterUrlResult(val lang: String, val id: String)

    fun parseHitsterUrl(url: String): HitsterUrlResult? {
        val regex = Regex("^(?:http://|https://)?www\\.hitstergame\\.com/(.+?)/(\\d+)$")
        val match = regex.find(url)

        if (match != null) {
            val (langRaw, idRaw) = match.destructured
            val processedLang = langRaw.replace("/", "-")
            val processedId = idRaw.toInt().toString()
            return HitsterUrlResult(processedLang, processedId)
        }

        val regexNordics = Regex("""^(?:http://|https://)?app\\.hitsternordics\\.com/resources/songs/(\\d+)\$""")
        val matchNordics = regexNordics.find(url)

        if (matchNordics != null) {
            val numberStr = matchNordics.groupValues[1]
            val numberInt = numberStr.toInt()
            val id = numberInt.toString()
            return HitsterUrlResult("nordics", id)
        }
        val regexHu = Regex("""^(?:http://|https://)?www\.hitstergame\.com/([^/]+)/([^/]+)/(\d+)$""")
        val matchHu = regexHu.find(url)

        if (matchHu != null) {
            val numberStr = matchHu.groupValues[1]
            val numberInt = numberStr.toInt()
            val id = numberInt.toString()
            return HitsterUrlResult("hu", id)
        }

        return null
    }


    fun getArtistAndTrack(context: android.content.Context, lang: String, cardId: String):Songinfo
    {
        val fileName = "hitster-$lang.csv"
        return try {
            context.assets.open(fileName).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",")
                    if (parts.size >= 4 && parts[0] == cardId) {

                      return  Songinfo(parts[1],parts[2]);
                    }
                }
                return  Songinfo("","")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return  Songinfo("","")
        }
    }

    fun loadYouTubeUrlFromCsv(context: android.content.Context, lang: String, cardId: String): String? {
        val fileName = "hitster-$lang.csv"
        return try {
            context.assets.open(fileName).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",")
                    if (parts.size >= 4 && parts[0] == cardId) {
                        return@useLines parts[4]
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


}