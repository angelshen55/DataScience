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

package com.aisleron.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API service for communicating with the recommendation model server
 */
interface ModelApiService {
    
    /**
     * Generate product recommendations based on a product name
     * @param request The generate request containing the product name
     * @return Response containing the prediction/recommendation
     */
    @POST("/v1/generate")
    suspend fun generateRecommendations(
        @Body request: GenerateRequest
    ): Response<GenerateResponse>
    
    /**
     * Retrain the model with new purchase set data
     * @param retrain Whether to retrain (should be true)
     * @param file The training data file (JSON format)
     * @return Response containing the retrain status message
     */
    @Multipart
    @POST("/v1/retrain")
    suspend fun retrainModel(
        @Part("retrain") retrain: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<RetrainResponse>
    
    /**
     * Check the health status of the model server
     */
    @GET("/health")
    suspend fun checkHealth(): Response<HealthResponse>
}

/**
 * Request model for generating recommendations
 */
data class GenerateRequest(
    val prompt: String,
    val max_new_tokens: Int? = null,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val do_sample: Boolean? = null
)

/**
 * Response model for generation
 */
data class GenerateResponse(
    val prediction: String
)

/**
 * Response model for retrain
 */
data class RetrainResponse(
    val message: String
)

/**
 * Response model for health check
 */
data class HealthResponse(
    val status: String,
    val device: String,
    val model: String,
    val adapter: String
)

