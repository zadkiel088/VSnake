package fr.eurecom.vsnake_test1

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class LocationStyleManager {

    private val client = OkHttpClient()

    private val apiKey = "hPcBEL6u69UArOdy2sau"

    fun fetchContinent(
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit
    ) {
        val url =
            "https://api.maptiler.com/geocoding/" +
                    "$longitude,$latitude.json" +
                    "?key=$apiKey"

        Log.d("DEBUG_MAP", "Reverse geocoding lat=$latitude lon=$longitude")

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("DEBUG_MAP_ONFAILURE", "MapTiler request failed", e)
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    Log.d("DEBUG_MAP_ONRESPONSE_1", "Raw JSON = $body")

                    val json = JSONObject(body)
                    val features = json.getJSONArray("features")

                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)

                        if (!feature.has("context")) continue

                        val context = feature.getJSONArray("context")

                        for (j in 0 until context.length()) {
                            val ctx = context.getJSONObject(j)

                            val categories = ctx.optJSONArray("categories") ?: continue

                            for (k in 0 until categories.length()) {
                                if (categories.getString(k) == "continent") {
                                    val continentName = ctx.getString("text")
                                    Log.d("DEBUG_MAP_ONRESPONSE_2", "Detected continent = $continentName")
                                    onResult(continentName.uppercase().replace(" ", "_"))
                                    return
                                }
                            }
                        }
                    }

                    Log.d("DEBUG_MAP_ONRESPONSE_3", "Continent not found")
                    onResult("UNKNOWN")
                } ?: onResult(null)
            }
        })
    }

    private fun mapCountryToContinent(country: String): String {
        return when (country.lowercase()) {
            "france", "austria", "germany", "italy", "spain" -> "EUROPE"
            "argentina", "brazil", "chile" -> "SOUTH_AMERICA"
            "niger", "nigeria", "morocco" -> "AFRICA"
            "united states", "canada","mexico" -> "NORTH_AMERICA"
            "china", "japan", "india" -> "ASIA"
            "australia" -> "OCEANIA"
            else -> "UNKNOWN"
        }
    }
}
