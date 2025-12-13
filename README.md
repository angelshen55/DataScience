# Project Documentation: Online Learning for Product Recommendation

This document provides an overview of the project's architecture, its core logic, and a step-by-step tutorial on how to integrate the backend service with an Android Studio application.

## 1. Project Overview

This project implements a product recommendation system with an "online learning" capability. It exposes a REST API that allows for both real-time product recommendations and continuous fine-tuning of the underlying language model based on new data.

The core idea is to use a LoRA (Low-Rank Adaptation) fine-tuned model to suggest products a customer might buy based on what they've already purchased. The "online" aspect means the model can be updated with new transaction data without any server downtime, allowing it to adapt to the latest purchasing trends.

### Core Components

- **`deploy.py`**: A FastAPI web server that serves the model. It provides two main endpoints:
  - `/v1/generate`: For getting product recommendations (inference).
  - `/v1/retrain`: For submitting new data to update the model.
- **`onlinelora.py`**: A Python script responsible for the model training logic. It takes new data, transforms it into a suitable format, and fine-tunes the existing LoRA adapter.
- **`requirements.txt`**: A list of all the Python dependencies required to run the project.
- **Model Adapters**: Directories like `qwen3-0.6B-lora-products/` and `qwen3-retrained-products-*/` store the LoRA adapter files, which contain the "knowledge" learned by the model.

## 2. How It Works: The Logic

### Step 1: Initial Setup
- The server is launched by running `python deploy.py`.
- It loads a base model (e.g., `Qwen/Qwen3-0.6B`) and attaches the most recently trained LoRA adapter to it. The path to this adapter is stored in `latest_adapter_path.txt`.

### Step 2: Getting Recommendations (Inference)
- A client application (like an Android app) sends a `POST` request to the `/v1/generate` endpoint.
- The request body contains a prompt, for example: `{"prompt": "顾客已购买「商品A」，请推测该顾客还可能一起购买的其他商品名称。"}`.
- The server uses the currently loaded model to generate a list of recommended products and sends it back in the response.

### Step 3: Continuous Improvement (Online Retraining)
- As new sales transactions occur, they can be collected into a JSON file.
- A client sends a `POST` request to the `/v1/retrain` endpoint with this new JSON data file.
- The server kicks off a background training process:
  1. The `onlinelora.py` script is called.
  2. The new data is transformed into instruction-answer pairs.
  3. The script loads the *current* LoRA adapter and continues training it with the new data.
  4. A new, updated LoRA adapter is saved to a new checkpoint directory.
- Once training is complete, the server **automatically and seamlessly** "hot-swaps" the old model adapter for the newly trained one.
- The server is now ready to provide even better recommendations based on the latest data, all without any interruption in service.

## 3. Android Studio Integration Tutorial

This tutorial explains how to build a simple Android app that interacts with the Python backend. The app will have a feature to get a product recommendation and another to upload new data for retraining.

### Prerequisites
1.  **Android Studio**: Installed and set up.
2.  **Running Backend**: The Python server (`deploy.py`) must be running.
3.  **Network Accessibility**: Your Android device or emulator must be ableto access the server. If running the server on your local machine and using an emulator, you can typically access it via the IP address `10.0.2.2`. For a physical device, both the device and the server must be on the same Wi-Fi network, and you'll use your computer's local IP address.

---

### Step 1: Set Up the Android Project

1.  Open Android Studio and create a **New Project** with an **Empty Views Activity**.
2.  Configure the project (e.g., name it "RecommendationApp", choose Kotlin).

### Step 2: Add Dependencies

We need libraries for making network requests and handling JSON. The most popular ones are Retrofit and OkHttp.

Open your `build.gradle.kts` (Module :app) file and add the following dependencies:

```kotlin
dependencies {
    // ... other dependencies

    // Retrofit for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp for logging network requests (optional but helpful)
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
}
```

Sync the project with the Gradle files.

### Step 3: Add Internet Permission

Open `app/src/main/AndroidManifest.xml` and add the internet permission:

```xml
<manifest ...>
    <uses-permission android:name="android.permission.INTERNET" />
    <application ...>
        ...
    </application>
</manifest>
```

### Step 4: Create the Retrofit API Service

This interface defines the API endpoints we will call.

1.  Create a new Kotlin file named `ApiService.kt`.
2.  Define the data classes for our requests and responses.
3.  Define the Retrofit interface.

```kotlin
// ApiService.kt
package com.example.recommendationapp

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// Data class for the /generate request body
data class GenerateRequest(val prompt: String)

// Data class for the /generate response
data class GenerateResponse(val prediction: String)

// Data class for the /retrain response
data class RetrainResponse(val message: String)

interface ApiService {
    @POST("/v1/generate")
    suspend fun getRecommendation(@Body request: GenerateRequest): Response<GenerateResponse>

    @Multipart
    @POST("/v1/retrain")
    suspend fun retrainModel(
        @Part("retrain") retrain: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<RetrainResponse>
}
```

### Step 5: Create a Retrofit Client

Create a singleton object to provide a configured Retrofit instance.

1.  Create a new Kotlin file named `RetrofitClient.kt`.

```kotlin
// RetrofitClient.kt
package com.example.recommendationapp

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // IMPORTANT: Replace with your server's address
    // Use http://10.0.2.2:8000 for Android Emulator
    // Use http://<YOUR-COMPUTER-IP>:8000 for a physical device on the same network
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}
```

### Step 6: Design the UI

Open `app/src/main/res/layout/activity_main.xml` and add some UI elements: an input field, a button to get recommendations, a text view to show the result, and a button to trigger retraining.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:gravity="center_horizontal"
    tools:context=".MainActivity">

    <EditText
        android:id="@+id/etProductInput"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Enter a purchased product name"
        android:inputType="text" />

    <Button
        android:id="@+id/btnGetRecommendation"
        android:layout_width="wrap_content"
        android_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Get Recommendation" />

    <TextView
        android:id="@+id/tvResult"
        android:layout_width="match_parent"
        androiduin_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="Result will be shown here"
        android:gravity="center" />

    <Button
        android:id="@+id/btnRetrain"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="32dp"
        android:text="Retrain with New Data" />

</LinearLayout>
```

### Step 7: Implement the Logic in `MainActivity.kt`

Now, wire up the UI to the API calls.

```kotlin
// MainActivity.kt
package com.example.recommendationapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    private lateinit var etProductInput: EditText
    private lateinit var btnGetRecommendation: Button
    private lateinit var tvResult: TextView
    private lateinit var btnRetrain: Button

    private val apiService = RetrofitClient.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etProductInput = findViewById(R.id.etProductInput)
        btnGetRecommendation = findViewById(R.id.btnGetRecommendation)
        tvResult = findViewById(R.id.tvResult)
        btnRetrain = findViewById(R.id.btnRetrain)

        btnGetRecommendation.setOnClickListener {
            val productName = etProductInput.text.toString()
            if (productName.isNotBlank()) {
                fetchRecommendation(productName)
            } else {
                Toast.makeText(this, "Please enter a product name", Toast.LENGTH_SHORT).show()
            }
        }

        btnRetrain.setOnClickListener {
            // In a real app, you would use a file picker to select a JSON file.
            // For this example, we use a hardcoded JSON string.
            uploadForRetraining()
        }
    }

    private fun fetchRecommendation(productName: String) {
        tvResult.text = "Loading..."
        lifecycleScope.launch {
            try {
                val prompt = "顾客已购买「$productName」，请推测该顾客还可能一起购买的其他商品名称。"
                val request = GenerateRequest(prompt)
                val response = apiService.getRecommendation(request)

                if (response.isSuccessful) {
                    tvResult.text = "Recommended: ${response.body()?.prediction}"
                } else {
                    tvResult.text = "Error: ${response.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                tvResult.text = "Failure: ${e.message}"
                Log.e("MainActivity", "API call failed", e)
            }
        }
    }

    private fun uploadForRetraining() {
        Toast.makeText(this, "Starting retraining process...", Toast.LENGTH_SHORT).show()

        // Example: New transaction data. In a real app, this would come from a file.
        val jsonContent = """
        {
          "Product": [
            ["New Product A", "New Product B", "New Product C"],
            ["Another Product X", "Another Product Y"]
          ]
        }
        """.trimIndent()

        lifecycleScope.launch {
            try {
                // Create the file part
                val requestFile = jsonContent.toRequestBody("application/json".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", "new_data.json", requestFile)

                // Create the 'retrain' boolean part
                val retrainFlag = "true".toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiService.retrainModel(retrainFlag, body)

                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, response.body()?.message, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Error: ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failure: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Retrain call failed", e)
            }
        }
    }
}
```

### Step 8: Run the App

You can now run the app on your emulator or physical device.

1.  Enter a product name and tap "Get Recommendation". The app will call the `/generate` endpoint and display the result.
2.  Tap "Retrain with New Data". The app will send the hardcoded JSON to the `/retrain` endpoint. The server will start training in the background, and the app will show a confirmation message. After a while, the model on the server will be updated.

This completes the integration. You have successfully connected an Android app to your powerful, continuously learning Python backend.
