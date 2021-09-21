@file:Suppress("TooManyFunctions", "ComplexMethod")

package com.woocommerce.android.extensions

import com.woocommerce.android.util.StringUtils
import com.woocommerce.android.util.WooLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.wordpress.android.fluxc.model.WCSSRModel
import java.text.SimpleDateFormat
import java.util.*
import org.apache.commons.io.FileUtils.byteCountToDisplaySize

const val MISSING_VALUE = "Info not found"
const val HEADING_WP_ENVIRONMENT = "WordPress Environment"
const val HEADING_SERVER_ENVIRONMENT = "Server Environment"
const val HEADING_DATABASE = "Database"
const val HEADING_SECURITY = "Security"
const val HEADING_ACTIVE_PLUGINS = "Active Plugins"
const val HEADING_SETTINGS = "Settings"
const val HEADING_PAGES = "WC Pages"
const val HEADING_THEME = "Theme"
const val HEADING_TEMPLATES = "Templates"
const val HEADING_STATUS_REPORT_INFORMATION = "Status report information"
const val CHECK = "✔"
const val NO_CHECK = "–"
const val PAGE_NOT_SET = "X Page not set"

fun WCSSRModel.formatResult(): String {
    var text = "### System Status Report generated via the WooCommerce Android app ### \n"

    // Environment
    environment?.let { it ->
        try {
            text += formatEnvironmentData(JSONObject(it))
        } catch (e: JSONException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
    }

    database?.let {
        try {
            text += formatDatabaseData(JSONObject(it))
        } catch (e: JSONException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
    }

    security?.let {
        try {
            text += formatSecurityData(JSONObject(it))
        } catch (e: JSONException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
    }

    activePlugins?.let {
        try {
            text += formatActivePluginsData(JSONArray(it))
        } catch (e: JSONException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
    }

    settings?.let {
        try {
            text += formatSettingsData(JSONObject(it))
        } catch (e: JSONException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
    }

    pages?.let {
        try {
            text += formatPagesData(JSONArray(it))
        } catch (e: JSONException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
    }

    theme?.let {
        try {
            text += formatThemeData(JSONObject(it))
        } catch (e: JSONException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
    }

    text += formatStatusReportInformationData()
    return text
}

private fun formatEnvironmentData(data: JSONObject): String {
    var text = formattedHeading(HEADING_WP_ENVIRONMENT)
    text += "WordPress Address (URL): ${data.optString("home_url", MISSING_VALUE)}\n"
    text += "Site Address (URL): ${data.optString("site_url", MISSING_VALUE)}\n"
    text += "WC Version: ${data.optString("version", MISSING_VALUE)}\n"
    text += "Log Directory Writable: ${checkIfTrue(data.optBoolean("log_directory_writable", false))}\n"
    text += "WP Version: ${data.optString("wp_version", MISSING_VALUE)}\n"
    text += "WP Multisite: ${checkIfTrue(data.optBoolean("wp_multisite", false))}\n"

    val memoryLimit = data.optString("wp_memory_limit", MISSING_VALUE)
    if (memoryLimit != MISSING_VALUE) {
        text += "WP Memory Limit: ${byteCountToDisplaySize(memoryLimit.toLong())}\n"
    }
    text += "WP Debug Mode: ${checkIfTrue(data.optBoolean("wp_debug_mode", false))}\n"
    text += "WP Cron: ${checkIfTrue(data.optBoolean("wp_cron", false))}\n"
    text += "Language: ${data.optString("language", MISSING_VALUE)}\n"
    text += "External object cache: ${checkIfTrue(data.optBoolean("external_object_cache", false))}\n"

    text += formattedHeading(HEADING_SERVER_ENVIRONMENT)
    text += "Server Info: ${data.optString("server_info", MISSING_VALUE)}\n"
    text += "PHP Version: ${data.optString("php_version", MISSING_VALUE)}\n"

    val postMaxSize = data.optString("php_post_max_size", MISSING_VALUE)
    if (postMaxSize != MISSING_VALUE) {
        text += "PHP Post Max Size: ${byteCountToDisplaySize(postMaxSize.toLong())}\n"
    }
    text += "PHP Time Limit: ${data.optString("php_max_execution_time", MISSING_VALUE)} s\n"
    text += "PHP Max input Vars: ${data.optString("php_max_input_vars", MISSING_VALUE)}\n"
    text += "cURL Version: ${data.optString("curl_version", MISSING_VALUE)}\n"
    text += "Suhosin installed: ${checkIfTrue(data.optBoolean("suhosin_installed", false))}\n"
    text += "MySQL Version: ${data.optString("mysql_version_string", MISSING_VALUE)}\n"

    val maxUploadSize = data.optString("max_upload_size", MISSING_VALUE)
    if (maxUploadSize != MISSING_VALUE) {
        text += "PHP Post Max Size: ${byteCountToDisplaySize(maxUploadSize.toLong())}\n"
    }
    text += "Default Timezone: ${data.optString("default_timezone", MISSING_VALUE)}\n"
    text += "fsockopen/cURL: ${checkIfTrue(data.optBoolean("fsockopen_or_curl_enabled", false))}\n"
    text += "SoapClient: ${checkIfTrue(data.optBoolean("soapclient_enabled", false))}\n"
    text += "DOMDocument: ${checkIfTrue(data.optBoolean("domdocument_enabled", false))}\n"
    text += "GZip: ${checkIfTrue(data.optBoolean("gzip_enabled", false))}\n"
    text += "Multibye String: ${checkIfTrue(data.optBoolean("mbstring_enabled", false))}\n"
    text += "Remote Post: ${checkIfTrue(data.optBoolean("remote_post_successful", false))}\n"
    text += "Remote Get: ${checkIfTrue(data.optBoolean("remote_get_successful", false))}\n"

    return text
}

private fun formatDatabaseData(data: JSONObject): String {
    var text = formattedHeading(HEADING_DATABASE)

    text += "WC Database Version: ${data.optString("wc_database_version", MISSING_VALUE)}\n"
    text += "WC Database Prefix: ${data.optString("wc_database_prefix", MISSING_VALUE)}\n"

    val sizeData = data.optJSONObject("database_size")
    sizeData?.let {
        val dataSize = it.optDouble("data", 0.0)
        val indexSize = it.optDouble("index", 0.0)
        val total = dataSize + indexSize
        text += "Total Database Size: " + if (total != 0.0) {
            total
        } else {
            MISSING_VALUE
        } + "\n"

        text += "Database Data Size: ${it.optString("data", MISSING_VALUE)}\n"
        text += "Database Index Size: ${it.optString("index", MISSING_VALUE)}\n"
    }

    val tablesData = data.optJSONObject("database_tables")
    tablesData?.let { it ->
        text += parseFormatTablesData(it, "woocommerce")
        text += parseFormatTablesData(it, "other")
    }

    return text
}

private fun formatSecurityData(data: JSONObject): String {
    var text = formattedHeading(HEADING_SECURITY)

    text += "Secure Connection (HTTPS): ${checkIfTrue(data.optBoolean("secure_connection", false))}\n"
    text += "Hide errors from visitors: ${checkIfTrue(data.optBoolean("hide_errors", false))}\n"

    return text
}

private fun formatActivePluginsData(data: JSONArray): String {
    var text = formattedHeading(HEADING_ACTIVE_PLUGINS)
    for (i in 0 until data.length()) {
        val plugin = data.optJSONObject(i)
        plugin?.let {
            text += plugin.optString("name", MISSING_VALUE)
            text += ": by " + plugin.optString("author_name", MISSING_VALUE)

            val currentVersion = plugin.optString("version", MISSING_VALUE)
            val latestVersion = plugin.optString("version_latest", MISSING_VALUE)
            text += " - $currentVersion"
            if (currentVersion != MISSING_VALUE &&
                latestVersion != MISSING_VALUE &&
                currentVersion != latestVersion
            ) {
                text += " (update to version $latestVersion available)"
            }
            text += "\n"
        }
    }
    return text
}

private fun formatSettingsData(data: JSONObject): String {
    var text = formattedHeading(HEADING_SETTINGS)
    text += "API Enabled: ${checkIfTrue(data.optBoolean("api_enabled", false))}\n"
    text += "Force SSL: ${checkIfTrue(data.optBoolean("force_ssl", false))}\n"

    // Currency format: currency_name(currency_symbol)
    // Correct value example: USD($)
    // Missing value example: Info not found(Info not found)
    text += "Currency: ${data.optString("currency", MISSING_VALUE)}"
    text += "("
    val currencySymbolHTML = data.optString("currency_symbol", MISSING_VALUE)
    text += if (currencySymbolHTML == MISSING_VALUE) {
        MISSING_VALUE
    } else {
        text += StringUtils.fromHtml(currencySymbolHTML)
    }
    text += ")\n"

    text += "Currency Position: ${data.optString("currency_position", MISSING_VALUE)}\n"
    text += "Thousand Separator: ${data.optString("thousand_separator", MISSING_VALUE)}\n"
    text += "Decimal Separator: ${data.optString("decimal_separator", MISSING_VALUE)}\n"
    text += "Number of Decimals: ${data.optString("number_of_decimals", MISSING_VALUE)}\n"

    val productTypesTaxonomy = data.optJSONObject("taxonomies")
    productTypesTaxonomy?.let {
        text += parseFormatTaxonomy(it, "Product Types") + "\n"
    }

    val productVisibilityTaxonomy = data.optJSONObject("product_visibility_terms")
    productVisibilityTaxonomy?.let {
        text += parseFormatTaxonomy(it, "Product Visibility") + "\n"
    }

    text += "Connected to WooCommerce.com: ${checkIfTrue(data.optBoolean("woocommerce_com_connected", false))}\n"

    return text
}

private fun formatPagesData(data: JSONArray): String {
    var text = formattedHeading(HEADING_PAGES)

    for (i in 0 until data.length()) {
        val page = data.optJSONObject(i)
        page?.let {
            text += "${it["page_name"]}: "
            val pageSet = it.optBoolean("page_set", false)
            text += if (pageSet) {
                " Page ID #${it["page_id"]}"
            } else {
                PAGE_NOT_SET
            }
            text += "\n"
        }
    }

    return text
}

private fun formatThemeData(data: JSONObject): String {
    var text = formattedHeading(HEADING_THEME)
    text += "Name: ${data.optString("name", MISSING_VALUE)}\n"

    val currentVersion = data.optString("version", MISSING_VALUE)
    val latestVersion = data.optString("version_latest", MISSING_VALUE)
    text += "Version: $currentVersion"
    if (currentVersion != MISSING_VALUE &&
        latestVersion != MISSING_VALUE &&
        currentVersion != latestVersion
    ) {
        text += " (update to version $latestVersion available)"
    }
    text += "\n"

    text += "Author URL: ${data.optString("author_url", MISSING_VALUE)}\n"

    val isChildTheme = data.optBoolean("is_child_theme", false)
    if (isChildTheme) {
        text += "Child Theme: ${CHECK}\n"
        text += "Parent Theme Name: ${data.optString("parent_name", MISSING_VALUE)}\n"

        val parentCurrentVersion = data.optString("parent_version", MISSING_VALUE)
        val parentLatestVersion = data.optString("parent_version_latest", MISSING_VALUE)
        text += "Parent Theme Version: $currentVersion"
        if (parentCurrentVersion != MISSING_VALUE &&
            parentLatestVersion != MISSING_VALUE &&
            parentCurrentVersion != parentLatestVersion
        ) {
            text += " (update to version $latestVersion available)"
        }
        text += "\n"

        text += "Parent Theme Author URL: ${data.optString("parent_author_url", MISSING_VALUE)}\n"
    }

    text += "WooCommerce support: ${checkIfTrue(data.optBoolean("has_woocommerce_support", false))}\n"
    text += "WooCommerce files: ${checkIfTrue(data.optBoolean("has_woocommerce_file", false))}\n"
    text += "Outdated templates: ${checkIfTrue(data.optBoolean("has_outdated_templates", false))}\n"

    val templates = data.optJSONArray("overrides")
    templates?.let { text += formatTemplatesData(it) }

    return text
}

private fun formatTemplatesData(data: JSONArray): String {
    var text = ""
    if (data.length() != 0) {
        text += formattedHeading(HEADING_TEMPLATES)
        text += "Overrides: "
        for (i in 0 until data.length()) {
            val template = data.optJSONObject(i)
            template?.let { item ->
                text += "${item.optString("file", MISSING_VALUE)}\n"
            }
        }
    }
    return text
}

private fun formatStatusReportInformationData(): String {
    var text = formattedHeading(HEADING_STATUS_REPORT_INFORMATION)

    val today = Calendar.getInstance()
    val ssrCreationTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(today.time)
    text += "Generated at: $ssrCreationTime"

    return text
}

private fun formattedHeading(text: String): String {
    return "\n ### $text ### \n\n"
}

private fun checkIfTrue(check: Boolean) = if (check) CHECK else NO_CHECK

private fun parseFormatTablesData(tables: JSONObject, tableType: String): String {
    var result = ""
    val tablesByType = tables.optJSONObject(tableType)

    tablesByType?.let { it ->
        it.keys().forEach { key ->
            val tableData = it.optJSONObject(key)
            tableData?.let { data ->
                result += "$key: " + parseFormatSingleTableData(data)
            }
        }
    }
    return result
}

// Example input: {"data":"0.05","index":"0.02","engine":"InnoDB"}
// Expected output: "Data: 0.05MB + Index: 0.02MB + Engine InnoDB"
private fun parseFormatSingleTableData(table: JSONObject): String {
    val data = table.optString("data", MISSING_VALUE)
    val index = table.optString("index", MISSING_VALUE)
    val engine = table.optString("engine", MISSING_VALUE)

    return "Data: ${data}MB + Index: ${index}MB + Engine $engine\n"
}

private fun parseFormatTaxonomy(taxonomies: JSONObject, taxonomyType: String): String {
    var text = "Taxonomies: $taxonomyType: \n"

    taxonomies.keys().forEach { key ->
        val value = taxonomies.get(key)
        text += "$key ($value)\n"
    }
    return text
}
