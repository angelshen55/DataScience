package com.aisleron.data.receipt

import com.aisleron.BuildConfig
import com.aisleron.domain.receipt.ReceiptItem
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

object ReceiptRemoteParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // 从 BuildConfig 读取百度凭证
    private val BAIDU_CLIENT_ID = BuildConfig.BAIDU_CLIENT_ID
    private val BAIDU_CLIENT_SECRET = BuildConfig.BAIDU_CLIENT_SECRET
    private const val BAIDU_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
    private const val BAIDU_RECEIPT_API_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/shopping_receipt"

    // 百度 OAuth 响应
    data class BaiduTokenResponse(
        val access_token: String?,
        val expires_in: Long?,
        val error: String?,
        val error_description: String?
    )

    // 百度小票识别响应数据类
    data class WordValue(val word: String?)
    data class TableRow(
        val product: WordValue?,
        val quantity: WordValue?,
        @SerializedName("unit_price")
        val unitPrice: WordValue?,
        @SerializedName("subtotal_amount")
        val subtotalAmount: WordValue?
    )
    data class ReceiptData(
        @SerializedName("shop_name")
        val shopName: List<WordValue>?,
        @SerializedName("consumption_date")
        val consumptionDate: List<WordValue>?,
        @SerializedName("total_amount")
        val totalAmount: List<WordValue>?,
        val table: List<TableRow>?
    )
    data class BaiduApiResponse(
        @SerializedName("words_result")
        val wordsResult: List<ReceiptData>?,
        val log_id: Long?,
        val error_code: Int?,
        val error_msg: String?
    )

    private fun isBarcode(name: String?): Boolean {
        if (name == null) return false
        val n = name.trim()
        return n.matches(Regex("^\\d{8,}$"))
    }

    private data class ParsedRow(
        val name: String?,
        val quantityText: String?,
        val unitPriceText: String?,
        val subtotalText: String?
    )

    private fun toReceiptItems(response: BaiduApiResponse): List<ReceiptItem> {
        val rawRows = response.wordsResult?.firstOrNull()?.table.orEmpty().map { r ->
            ParsedRow(
                name = r.product?.word?.trim(),
                quantityText = r.quantity?.word?.trim(),
                unitPriceText = r.unitPrice?.word?.trim(),
                subtotalText = r.subtotalAmount?.word?.trim()
            )
        }

        val merged = mutableListOf<ParsedRow>()
        var i = 0
        while (i < rawRows.size) {
            val cur = rawRows[i]
            if (i + 1 < rawRows.size) {
                val next = rawRows[i + 1]
                val curBarcode = isBarcode(cur.name)
                val nextBarcode = isBarcode(next.name)
                if ((curBarcode && !nextBarcode) || (!curBarcode && nextBarcode)) {
                    val textRow = if (!curBarcode) cur else next
                    val codeRow = if (curBarcode) cur else next
                    merged.add(
                        ParsedRow(
                            name = textRow.name,
                            quantityText = textRow.quantityText ?: codeRow.quantityText,
                            unitPriceText = textRow.unitPriceText ?: codeRow.unitPriceText,
                            subtotalText = textRow.subtotalText ?: codeRow.subtotalText
                        )
                    )
                    i += 2
                    continue
                }
            }
            merged.add(cur)
            i += 1
        }

        val items = merged.mapNotNull { pr ->
            val name = pr.name?.trim()
            if (name.isNullOrEmpty()) return@mapNotNull null

            var qty = pr.quantityText?.toDoubleOrNull()
            var unitPrice = pr.unitPriceText?.let {
                try { BigDecimal(it) } catch (_: Exception) { null }
            }
            var subtotal = pr.subtotalText?.let {
                try { BigDecimal(it) } catch (_: Exception) { null }
            }

            if (unitPrice == null && subtotal != null && qty != null && qty > 0.0) {
                try {
                    unitPrice = subtotal.divide(java.math.BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP)
                } catch (_: Exception) {}
            }

            if ((qty == null || qty <= 0.0) && subtotal != null && unitPrice != null && unitPrice > BigDecimal.ZERO) {
                try {
                    qty = subtotal.divide(unitPrice, 2, java.math.RoundingMode.HALF_UP).toDouble()
                } catch (_: Exception) {}
            }

            if (subtotal == null && unitPrice != null && qty != null && qty > 0.0) {
                subtotal = unitPrice.multiply(java.math.BigDecimal.valueOf(qty))
            }

            val finalQty = qty ?: 0.0
            val finalUnitPrice = unitPrice ?: BigDecimal.ZERO

            ReceiptItem(name = name, unitPrice = finalUnitPrice, quantity = finalQty)
        }

        val grouped = items.groupBy { Pair(it.name.trim().lowercase(), it.unitPrice) }
        return grouped.map { (_, list) ->
            val first = list.first()
            val totalQty = list.sumOf { it.quantity }
            ReceiptItem(name = first.name, unitPrice = first.unitPrice, quantity = totalQty)
        }
    }

    /**
     * 获取百度 access_token
     */
    private suspend fun getBaiduAccessToken(): String = withContext(Dispatchers.IO) {
        val url = "$BAIDU_TOKEN_URL?client_id=$BAIDU_CLIENT_ID&client_secret=$BAIDU_CLIENT_SECRET&grant_type=client_credentials"
        
        val body = RequestBody.create("application/json".toMediaType(), "")
        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("获取 token 失败: ${resp.code}")
            val text = resp.body?.string().orEmpty()
            
            val tokenResp = try {
                gson.fromJson(text, BaiduTokenResponse::class.java)
            } catch (e: Exception) {
                throw Exception("解析 token 响应失败: ${e.message}")
            }

            if (!tokenResp.access_token.isNullOrEmpty()) {
                tokenResp.access_token
            } else {
                throw Exception("获取 token 失败: ${tokenResp.error} - ${tokenResp.error_description}")
            }
        }
    }

    /**
     * 调用百度小票识别 API
     */
    private suspend fun callBaiduReceiptApi(base64Image: String, accessToken: String): List<ReceiptItem> = withContext(Dispatchers.IO) {
        val url = "$BAIDU_RECEIPT_API_URL?access_token=$accessToken"
        
        val formBody = FormBody.Builder()
            .add("image", base64Image)
            .build()

        val req = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("百度 API 错误: ${resp.code}")
            val text = resp.body?.string().orEmpty()
            
            val response = try {
                gson.fromJson(text, BaiduApiResponse::class.java)
            } catch (e: Exception) {
                throw Exception("解析百度响应失败: ${e.message}")
            }

            // 检查 API 错误
            if (response.error_code != null && response.error_code != 0) {
                throw Exception("百度 API 返回错误: ${response.error_msg}")
            }

            toReceiptItems(response)
        }
    }

    /**
     * 主入口：自动获取 token 并调用识别 API
     */
    suspend fun parseImageBase64(base64Image: String): List<ReceiptItem> = withContext(Dispatchers.IO) {
        val accessToken = getBaiduAccessToken()
        callBaiduReceiptApi(base64Image, accessToken)
    }
}
