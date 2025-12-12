/*
 * Copyright (C) 2025 aisleron.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aisleron.domain.receipt

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

data class ReceiptItemRaw(
    val rawLine: String,
    val name: String?,
    val unitPriceText: String?,
    val confidence: Float = 1.0f
)

data class ReceiptItem(
    val name: String,
    val unitPrice: BigDecimal,
    val quantity: Double = 0.0
) {
    val totalPrice: BigDecimal
        get() = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity))
}

data class ReceiptParseResult(
    val items: List<ReceiptItem>,
    val ignoredLines: List<String>,
    val errors: List<String>
)

/*
 * 兼容版本的收据解析器 - 保持原有API，内部使用智能解析
 */
object ReceiptParser {

    // 新增：扩展的解析结果，包含更多信息
    data class DetailedParseResult(
        val items: List<ReceiptItem>,
        val ignoredLines: List<String>,
        val errors: List<String>,
        val totalAmount: BigDecimal?,
        val storeName: String?,
        val date: String?
    )

    // 内部使用的智能解析器配置
    private data class ParserConfig(
        val locale: Locale = Locale.getDefault(),
        val maxPrice: BigDecimal = BigDecimal("100000"),
        val minPrice: BigDecimal = BigDecimal.ZERO,
        val enableMultiLineDetection: Boolean = true,
        val enableQuantityDetection: Boolean = true,
        val enableStoreDetection: Boolean = true
    )

    // 保持原有的API不变
    fun parse(text: String, locale: Locale = Locale.getDefault()): ReceiptParseResult {
        val config = ParserConfig(locale = locale)
        val detailedResult = parseWithConfig(text, config)

        // 转换为原有的结果格式
        return ReceiptParseResult(
            items = detailedResult.items,
            ignoredLines = detailedResult.ignoredLines,
            errors = detailedResult.errors
        )
    }

    // 新增：提供详细解析结果的API
    fun parseDetailed(text: String, locale: Locale = Locale.getDefault()): DetailedParseResult {
        val config = ParserConfig(locale = locale)
        return parseWithConfig(text, config)
    }

    // 内部解析实现
    private fun parseWithConfig(text: String, config: ParserConfig): DetailedParseResult {
        val lines = preprocessText(text)
        if (lines.isEmpty()) {
            return DetailedParseResult(emptyList(), emptyList(), emptyList(), null, null, null)
        }

        val items = mutableListOf<ReceiptItem>()
        val ignored = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var totalAmount: BigDecimal? = null
        var storeName: String? = null
        var date: String? = null

        // 提取商店名称和日期
        if (config.enableStoreDetection) {
            storeName = detectStoreName(lines)
            date = detectDate(lines)
        }

        // 解析商品信息
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            when {
                isHeaderOrFooter(line) -> {
                    ignored.add(line)
                    i++
                }
                isTotalLine(line) -> {
                    totalAmount = extractTotalAmount(line, config.locale) ?: totalAmount
                    ignored.add(line)
                    i++
                }
                else -> {
                    val parseResult = parseItemAtPosition(lines, i, config)
                    if (parseResult.success) {
                        items.add(parseResult.item!!)
                        i += parseResult.linesConsumed
                    } else {
                        // 尝试组合行解析
                        if (i + 1 < lines.size && config.enableMultiLineDetection) {
                            val combinedResult = parseCombinedLines(lines[i], lines[i + 1], config)
                            if (combinedResult.success) {
                                items.add(combinedResult.item!!)
                                i += 2
                            } else {
                                ignored.add(line)
                                i++
                            }
                        } else {
                            ignored.add(line)
                            i++
                        }
                    }
                }
            }
        }

        return DetailedParseResult(
            items = mergeSimilarItems(items),
            ignoredLines = ignored,
            errors = errors,
            totalAmount = totalAmount,
            storeName = storeName,
            date = date
        )
    }

    // 价格模式
    private val pricePatterns = listOf(
        Regex("""(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2}))\b"""),
        Regex("""\b(\d+[.,]\d{2})\b"""),
        Regex("""[￥¥€£\$]\s*(\d+[.,]\d{0,2})"""),
        Regex("""(\d+)[\s]*[xX*][\s]*(\d+[.,]\d{0,2})""")
    )

    // 关键词定义
    private val totalKeywords = setOf(
        "total", "subtotal", "tax", "vat", "amount", "balance", "change", "due",
        "合计", "总计", "金额", "应收", "实收", "找零", "税额", "小计", "总额"
    )

    private val quantityKeywords = setOf("组", "个", "件", "包", "瓶", "袋", "盒", "x", "X", "*")

    // 解析单个位置的项目
    private fun parseItemAtPosition(lines: List<String>, index: Int, config: ParserConfig): ItemParseResult {
        val line = lines[index]

        if (isLikelyNonItem(line)) {
            return ItemParseResult(success = false, shouldSkip = true)
        }

        // 单行解析
        val singleLineResult = parseSingleLine(line, config)
        if (singleLineResult.success) {
            return singleLineResult.copy(linesConsumed = 1)
        }

        // 多行解析
        if (config.enableMultiLineDetection && index + 1 < lines.size) {
            val nextLine = lines[index + 1]
            val multiLineResult = parseMultiLine(line, nextLine, config)
            if (multiLineResult.success) {
                return multiLineResult.copy(linesConsumed = 2)
            }
        }

        return ItemParseResult(success = false, shouldSkip = false)
    }

    // 单行解析
    private fun parseSingleLine(line: String, config: ParserConfig): ItemParseResult {
        val prices = findAllPrices(line)
        if (prices.isEmpty()) return ItemParseResult(success = false)

        val lastPrice = prices.last()
        val priceValue = parsePrice(lastPrice.value, config.locale) ?: return ItemParseResult(success = false)

        if (!isValidPrice(priceValue, config)) return ItemParseResult(success = false)

        val name = extractItemName(line, prices, config)
        if (name.isBlank()) return ItemParseResult(success = false)

        val quantity = if (config.enableQuantityDetection) extractQuantity(line, priceValue) else 0.0

        return ItemParseResult(
            success = true,
            item = ReceiptItem(name, priceValue, quantity)
        )
    }

    // 多行解析
    private fun parseMultiLine(line1: String, line2: String, config: ParserConfig): ItemParseResult {
        if (containsPrice(line1) && containsPrice(line2)) {
            return ItemParseResult(success = false)
        }

        val nameLine = if (!containsPrice(line1)) line1 else line2
        val priceLine = if (containsPrice(line2)) line2 else line1

        if (nameLine == priceLine) return ItemParseResult(success = false)

        val prices = findAllPrices(priceLine)
        if (prices.isEmpty()) return ItemParseResult(success = false)

        val priceValue = parsePrice(prices.last().value, config.locale) ?: return ItemParseResult(success = false)
        if (!isValidPrice(priceValue, config)) return ItemParseResult(success = false)

        val name = extractItemName(nameLine, emptyList(), config)
        if (name.isBlank()) return ItemParseResult(success = false)

        val quantity = if (config.enableQuantityDetection) extractQuantity(line1, priceValue) else 0.0

        return ItemParseResult(
            success = true,
            item = ReceiptItem(name, priceValue, quantity)
        )
    }

    // 组合行解析
    private fun parseCombinedLines(line1: String, line2: String, config: ParserConfig): ItemParseResult {
        val combined = "$line1 $line2"
        return parseSingleLine(combined, config)
    }

    // 工具方法
    private fun preprocessText(text: String): List<String> {
        return text.lines()
            .map { it.trim() }
            .map { it.replace('\u00A0', ' ') }
            .map { it.replace(Regex("[　]+"), " ") }
            .filter { it.isNotBlank() }
    }

    private fun findAllPrices(text: String): List<MatchResult> {
        return pricePatterns.flatMap { it.findAll(text) }.sortedBy { it.range.first }
    }

    private fun containsPrice(text: String): Boolean {
        return pricePatterns.any { it.containsMatchIn(text) }
    }

    private fun parsePrice(priceText: String, locale: Locale): BigDecimal? {
        val cleaned = priceText
            .replace(Regex("[^0-9.,-]"), "")
            .replace(Regex(",(?=\\d{3})"), "")
            .replace(',', '.')

        return cleaned.toBigDecimalOrNull()
    }

    private fun isValidPrice(price: BigDecimal, config: ParserConfig): Boolean {
        return price >= config.minPrice && price <= config.maxPrice
    }

    private fun extractItemName(line: String, prices: List<MatchResult>, config: ParserConfig): String {
        // 使用 StringBuilder 构建新字符串，只包含不在价格范围内的字符
        // 这样可以避免索引越界问题
        val nameBuilder = StringBuilder()
        val sortedPrices = prices.sortedBy { it.range.first }
        var lastIndex = 0

        sortedPrices.forEach { priceMatch ->
            val start = priceMatch.range.first
            val end = priceMatch.range.last + 1

            // 添加价格之前的文本
            if (start > lastIndex) {
                nameBuilder.append(line.substring(lastIndex, start))
            }

            lastIndex = maxOf(lastIndex, end)
        }

        // 添加最后一个价格之后的文本
        if (lastIndex < line.length) {
            nameBuilder.append(line.substring(lastIndex))
        }

        var name = nameBuilder.toString()

        if (config.enableQuantityDetection) {
            quantityKeywords.forEach { keyword ->
                val escapedKeyword = Regex.escape(keyword)
                name = name.replace(Regex("\\s*\\d+\\s*$escapedKeyword\\s*", RegexOption.IGNORE_CASE), " ")
            }
        }

        name = name.replace(Regex("^\\d+\\s*"), "")

        name = name
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix("-")
            .removeSuffix(":")
            .trim()

        return name
    }

    private fun extractQuantity(line: String, unitPrice: BigDecimal): Double {
        val patterns = listOf(
            Regex("(\\d+)\\s*(组|个|件|包|瓶|袋|盒)"),
            Regex("(\\d+)\\s*[xX*]"),
            Regex("x\\s*(\\d+)\\b", RegexOption.IGNORE_CASE)
        )

        patterns.forEach { pattern ->
            val match = pattern.find(line)
            if (match != null) {
                return match.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }

        return 0.0
    }

    private fun isHeaderOrFooter(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("欢迎光临") ||
                lower.contains("谢谢惠顾") ||
                lower.contains("小票") ||
                lower.contains("凭证") ||
                lower.contains("receipt") ||
                lower.contains("thank") ||
                lower.matches(Regex("^[*=]+$"))
    }

    private fun isTotalLine(line: String): Boolean {
        val lower = line.lowercase()
        return totalKeywords.any { lower.contains(it) }
    }

    private fun isLikelyNonItem(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("单号:") ||
                lower.contains("机号:") ||
                lower.contains("时间:") ||
                lower.contains("工号:") ||
                lower.contains("序") && lower.contains("品名") ||
                lower.matches(Regex("^\\d+$")) ||
                lower.length < 2
    }

    private fun extractTotalAmount(line: String, locale: Locale): BigDecimal? {
        val prices = findAllPrices(line)
        return prices.lastOrNull()?.let { parsePrice(it.value, locale) }
    }

    private fun detectStoreName(lines: List<String>): String? {
        val candidateLines = lines.take(3)
        return candidateLines.firstOrNull { line ->
            !line.contains("单号:") &&
                    !line.contains("时间:") &&
                    !line.contains("序") &&
                    line.length in 2..50 &&
                    !line.matches(Regex("^[*=]+$"))
        }
    }

    private fun detectDate(lines: List<String>): String? {
        val datePattern = Regex("""(\d{4}[-./]\d{1,2}[-./]\d{1,2})|(\d{1,2}[-./]\d{1,2}[-./]\d{4})""")
        for (line in lines) {
            val match = datePattern.find(line)
            if (match != null) {
                return match.value
            }
        }
        return null
    }

    private fun mergeSimilarItems(items: List<ReceiptItem>): List<ReceiptItem> {
        val merged = mutableMapOf<String, ReceiptItem>()

        items.forEach { item ->
            val key = "${item.name.lowercase()}@${item.unitPrice}"
            val existing = merged[key]
            if (existing == null) {
                merged[key] = item
            } else {
                merged[key] = existing.copy(quantity = existing.quantity + item.quantity)
            }
        }

        return merged.values.toList()
    }

    // 内部结果类
    private data class ItemParseResult(
        val success: Boolean,
        val item: ReceiptItem? = null,
        val linesConsumed: Int = 1,
        val shouldSkip: Boolean = false
    )
}
