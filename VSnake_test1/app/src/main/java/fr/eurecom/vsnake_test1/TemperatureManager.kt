package fr.eurecom.vsnake_test1

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class TemperatureManager {

    private val client = OkHttpClient()

    fun fetchTemperature(
        latitude: Double,
        longitude: Double,
        onResult: (Double?) -> Unit
    ) {
        val url =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude" +
                    "&longitude=$longitude" +
                    "&current_weather=true"

        val request = Request.Builder().url(url).build()

        Log.d("DEBUG_TEMPERATUREMANAGER_FECTCHTEMPERATURE_1", "Latitude=$latitude Longitude=$longitude")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DEBUG_TEMPERATUREMANAGER_ONFAILURE", "Failed to fetch temperature", e)
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val json = JSONObject(it)
                    val temp =
                        json.getJSONObject("current_weather")
                            .getDouble("temperature")
                    Log.d("DEBUG_TEMPERATUREMANAGER_ONRESPONSE_1", "Raw temperature=$temp °C")
                    onResult(temp)
                } ?: run {
                    Log.e("DEBUG_TEMPERATUREMANAGER_ONRESPONSE_2", "Empty response body")
                    onResult(null)
                }
            }
        })
    }
}
