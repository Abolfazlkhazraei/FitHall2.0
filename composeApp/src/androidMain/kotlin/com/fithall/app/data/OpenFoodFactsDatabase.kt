package com.fithall.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Looks barcodes up in Open Food Facts, a free, open product database (no API key needed). */
class OpenFoodFactsDatabase : FoodDatabase {

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun lookupBarcode(barcode: String): PackagedFood? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(OpenFoodFacts.productUrl(barcode))
            // Open Food Facts asks apps to identify themselves.
            .header("User-Agent", "FitHall/1.0 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            // Unknown products usually come back as 200 with status 0 (handled by parse), sometimes as 404.
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("Open Food Facts returned HTTP ${response.code}")
            OpenFoodFacts.parse(barcode, response.body?.string().orEmpty())
        }
    }
}
