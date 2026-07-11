package com.bikeability.commute.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
private const val HOURLY_FIELDS =
    "temperature_2m,relative_humidity_2m,precipitation,precipitation_probability," +
        "cloud_cover,wind_speed_10m,shortwave_radiation,apparent_temperature"

class OpenMeteoClient(
    private val http: HttpClient = HttpClient(OkHttp),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * One request covering every point: comma-separated coordinates make
     * Open-Meteo return one forecast block per point, in order.
     */
    suspend fun fetch(points: List<Pair<Double, Double>>): List<ForecastResponse> {
        require(points.isNotEmpty())
        val lat = points.joinToString(",") { it.first.toString() }
        val lon = points.joinToString(",") { it.second.toString() }
        val url = "$BASE_URL?latitude=$lat&longitude=$lon" +
            "&hourly=$HOURLY_FIELDS" +
            "&temperature_unit=celsius&wind_speed_unit=ms&precipitation_unit=mm" +
            "&timezone=auto&forecast_days=2"

        val response = http.get(url)
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw OpenMeteoException("Open-Meteo HTTP ${response.status.value}: ${body.take(200)}")
        }
        // A single point returns an object; multiple points return an array.
        val element: JsonElement = json.parseToJsonElement(body)
        return if (element is JsonArray) {
            element.map { json.decodeFromJsonElement(ForecastResponse.serializer(), it) }
        } else {
            listOf(json.decodeFromJsonElement(ForecastResponse.serializer(), element))
        }
    }
}

class OpenMeteoException(message: String, cause: Throwable? = null) : Exception(message, cause)
