package com.example.mycarapp.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.mycarapp.R
import com.example.mycarapp.controller.ApiServiceFactory
import com.example.mycarapp.dto.Account
import com.example.mycarapp.dto.LoginRequest
import com.example.mycarapp.HiltModule.ConfigManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddAccountActivity : AppCompatActivity() {

    @Inject
    lateinit var configManager: ConfigManager

    @Inject
    lateinit var apiServiceFactory: ApiServiceFactory

    private lateinit var serverUrlEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_account)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        serverUrlEditText = findViewById(R.id.server_url_edit_text)
        usernameEditText = findViewById(R.id.username_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        saveButton = findViewById(R.id.save_button)
    }

    private fun setupListeners() {
        saveButton.setOnClickListener {
            addAccount()
        }
    }

    private fun addAccount() {
        val serverUrl = serverUrlEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = apiServiceFactory.createPublicApiService(serverUrl)
                val loginRequest = LoginRequest(username = username, password = password)
                val response = apiService.login(loginRequest)

                if (response.isSuccessful && response.body() != null) {
                    val loginResult = response.body()!!

                    val account = Account(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        authToken = loginResult.token,
                        subsonicSalt = loginResult.subsonicSalt,
                        subsonicToken = loginResult.subsonicToken,
                        isActive = true
                    )

                    val accountId = configManager.addAccount(account)
                    configManager.setActiveAccount(accountId.toInt())

                    runOnUiThread {
                        Toast.makeText(this@AddAccountActivity, "Konto dodane pomyślnie", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AddAccountActivity, "Błąd logowania: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@AddAccountActivity, "Błąd sieci: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}