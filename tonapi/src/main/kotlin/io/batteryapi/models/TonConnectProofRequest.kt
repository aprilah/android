/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package io.batteryapi.models

import io.batteryapi.models.TonConnectProofRequestProof

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param address 
 * @param proof 
 */


data class TonConnectProofRequest (

    @Json(name = "address")
    val address: kotlin.String,

    @Json(name = "proof")
    val proof: TonConnectProofRequestProof

) {


}

