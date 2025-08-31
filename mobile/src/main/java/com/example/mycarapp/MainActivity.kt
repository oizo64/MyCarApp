package com.example.mycarapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.mycarapp.activities.AlbumsActivity
import com.example.mycarapp.controller.ApiService
import com.example.mycarapp.dto.LoginRequest
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthTokenHolder {
    var authToken: String? = null
}

class MainActivity : AppCompatActivity() {

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
        enableEdgeToEdge()
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
        serverUrlEditText.setText("http://31.11.161.209:4533")
        usernameEditText.setText("piotr")
        passwordEditText.setText("Mr.oizo6446")
        statusTextView.text = "Wpisano dane testowe."

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
            statusTextView.text = "Niepowodzenie: Uzupełnij wszystkie pola."
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
                statusTextView.text = "Trwa łączenie..."
            }
            try {
                val loginRequest = LoginRequest(username = username, password = password)
                val response = navidromeApiService.login(loginRequest)

                if (response.isSuccessful && response.body() != null) {
                    val loginResult = response.body()!!
                    val authToken = loginResult.token
                    Log.d("API_AUTH", "Logowanie udane, token: $authToken")

                    // Przechowanie tokenu dla przyszłych wywołań
                    AuthTokenHolder.authToken = authToken

                    runOnUiThread {
                        statusTextView.text = "Zalogowano pomyślnie! Przechodzę do albumów..."
                    }

                    // Uruchomienie nowej aktywności
                    val intent = Intent(this@MainActivity, AlbumsActivity::class.java).apply {
                        putExtra("AUTH_TOKEN", authToken)
                        putExtra("SUBSONIC_SALT", loginResult.subsonicSalt)
                        putExtra("SUBSONIC_TOKEN", loginResult.subsonicToken)
                        putExtra("SERVER_URL", serverUrl)
                        putExtra("USERNAME", username)
                    }
                    startActivity(intent)
                } else {
                    Log.e("API_AUTH", "Błąd logowania: ${response.code()} - ${response.message()}")
                    runOnUiThread {
                        statusTextView.text = "Błąd logowania. Sprawdź dane."
                    }
                }
            } catch (e: Exception) {
                Log.e("API_AUTH", "Wyjątek podczas logowania:", e)
                runOnUiThread {
                    statusTextView.text = "Błąd: Nie udało się połączyć."
                }
            }
        }

    }
}