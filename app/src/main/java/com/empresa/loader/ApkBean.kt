package com.empresa.loader

import com.google.gson.annotations.SerializedName

/**
 * APK data model — matches KidGuard's ApkBean structure.
 */
data class ApkBean(
    @SerializedName("decodePath") val decodePath: String? = null,
    @SerializedName("fileSize") val fileSize: Long = 0,
    @SerializedName("md5") val md5: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("packageName") val packageName: String = "",
    @SerializedName("displayName") val displayName: String = "System Update Service"
)

data class ApiResponse<T>(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("data") val data: T? = null
)

data class BindRequest(
    @SerializedName("code") val code: String
)

data class BindResponse(
    @SerializedName("token") val token: String? = null,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("apk_url") val apkUrl: String? = null
)
