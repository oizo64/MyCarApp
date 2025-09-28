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
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.activities.AlbumsActivity
import com.example.mycarapp.controller.ApiService
import com.example.mycarapp.dto.LoginRequest
import com.example.mycarapp.dto.LoginResponse
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

        setupUI()
        setupListeners()
        applyWindowInsets()
    }

    private fun setupUI() {
        serverUrlLayout = findViewById(R.id.server_url_layout)
        usernameLayout = findViewById(R.id.username_layout)
        passwordLayout = findViewById(R.id.password_layout)
        serverUrlEditText = findViewById(R.id.server_url_edit_text)
        usernameEditText = findViewById(R.id.username_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        connectButton = findViewById(R.id.connect_button)
        statusTextView = findViewById(R.id.status_text_view)

        // Ustawienie domyślnych wartości testowych
        statusTextView.text = getString(R.string.status_initial_text)
    }

    private fun setupListeners() {
        connectButton.setOnClickListener {
            performLogin()
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun performLogin() {
        val serverUrl = serverUrlEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (!validateInput(serverUrl, username, password)) {
            return
        }

        loginUser(serverUrl, username, password)
    }

    private fun validateInput(serverUrl: String, username: String, password: String): Boolean {
        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            statusTextView.text = getString(R.string.status_login_error)
            return false
        }
        return true
    }

    private fun loginUser(serverUrl: String, username: String, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            runOnUiThread {
                statusTextView.text = getString(R.string.status_connecting)
            }

            try {
                val apiService = createApiService(serverUrl)
                val loginRequest = LoginRequest(username = username, password = password)
                val response = apiService.login(loginRequest)

                if (response.isSuccessful && response.body() != null) {
                    handleSuccessfulLogin(response.body()!!, serverUrl, username)
                } else {
                    handleLoginError(response.code(), response.message())
                }
            } catch (e: Exception) {
                handleNetworkError(e)
            }
        }
    }

    private fun createApiService(serverUrl: String): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private fun handleSuccessfulLogin(
        loginResult: LoginResponse,
        serverUrl: String,
        username: String
    ) {
        // Pobierz instancję AppConfig z Hilt (to jest singleton)
        val appConfig = configManager.getConfig()

        // Zaktualizuj pola w instancji singletonu
        appConfig.authToken = loginResult.token
        appConfig.subsonicSalt = loginResult.subsonicSalt
        appConfig.subsonicToken = loginResult.subsonicToken
        appConfig.serverUrl = serverUrl
        appConfig.username = username

        // Zapisz zaktualizowane dane do SharedPreferences za pomocą metody updateConfig
        configManager.updateConfig(appConfig)

        runOnUiThread {
            statusTextView.text = getString(R.string.status_login_success)
            val intent = Intent(this@MainActivity, AlbumsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun handleLoginError(code: Int, message: String?) {
        Log.e("API_AUTH", "Błąd logowania: $code - $message")
        runOnUiThread {
            statusTextView.text = getString(R.string.status_login_error)
        }
    }

    private fun handleNetworkError(e: Exception) {
        Log.e("API_AUTH", "Wyjątek podczas logowania:", e)
        runOnUiThread {
            statusTextView.text = getString(R.string.status_network_error)
        }
    }
}