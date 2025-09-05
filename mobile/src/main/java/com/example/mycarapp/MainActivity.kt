package com.example.mycarapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.mycarapp.activities.AlbumsActivity
import com.example.mycarapp.controller.ApiService
import com.example.mycarapp.dto.LoginRequest
import com.example.mycarapp.HiltModule.AppConfig
import com.example.mycarapp.HiltModule.AppModule
import com.example.mycarapp.HiltModule.ConfigManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var serverUrlLayout: TextInputLayout
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var serverUrlEditText: TextInputEditText
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var connectButton: Button
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicjalizacja widoków
        serverUrlLayout = findViewById(R.id.server_url_layout)
        usernameLayout = findViewById(R.id.username_layout)
        passwordLayout = findViewById(R.id.password_layout)
        serverUrlEditText = findViewById(R.id.server_url_edit_text)
        usernameEditText = findViewById(R.id.username_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        connectButton = findViewById(R.id.connect_button)
        statusTextView = findViewById(R.id.status_text_view)

        // Ustawienie domyślnych wartości testowych
        serverUrlEditText.setText(getString(R.string.server_address))
        usernameEditText.setText(getString(R.string.login))
        passwordEditText.setText(getString(R.string.password))
        statusTextView.text = getString(R.string.status_initial_text)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        connectButton.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        // Pobranie wartości z pól
        val serverUrl = serverUrlEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // Prosta walidacja
        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            statusTextView.text = getString(R.string.status_login_error)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // Zbudowanie klienta HTTP z interceptorem logującym
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            // Zbudowanie instancji Retrofit
            val retrofit = Retrofit.Builder()
                .baseUrl("$serverUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val navidromeApiService = retrofit.create(ApiService::class.java)

            // Aktualizacja statusu na UI (na wątku głównym)
            runOnUiThread {
                statusTextView.text = getString(R.string.status_connecting)
            }

            try {
                val loginRequest = LoginRequest(username = username, password = password)
                val response = navidromeApiService.login(loginRequest)

                if (response.isSuccessful && response.body() != null) {
                    val loginResult = response.body()!!
                    val authToken = loginResult.token
                    Log.d("API_AUTH", "Logowanie udane, token: $authToken")

                    // Zapisanie danych do konfiguracji przez ConfigManager
                    val appConfig = AppConfig(
                        authToken = authToken,
                        subsonicSalt = loginResult.subsonicSalt,
                        subsonicToken = loginResult.subsonicToken,
                        serverUrl = serverUrl,
                        username = username
                    )

                    configManager.updateConfig(appConfig)

                    runOnUiThread {
                        statusTextView.text = getString(R.string.status_login_success)
                    }

                    // Uruchomienie nowej aktywności
                    val intent = Intent(this@MainActivity, AlbumsActivity::class.java)
                    startActivity(intent)

                    // Opcjonalnie: zakończ aktualną aktywność
                    finish()

                } else {
                    Log.e("API_AUTH", "Błąd logowania: ${response.code()} - ${response.message()}")
                    runOnUiThread {
                        statusTextView.text = getString(R.string.status_login_error)
                    }
                }
            } catch (e: Exception) {
                Log.e("API_AUTH", "Wyjątek podczas logowania:", e)
                runOnUiThread {
                    statusTextView.text = getString(R.string.status_network_error)
                }
            }
        }
    }
}