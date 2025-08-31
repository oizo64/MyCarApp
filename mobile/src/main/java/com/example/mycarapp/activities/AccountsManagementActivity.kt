package com.example.mycarapp.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.R
import com.example.mycarapp.adapters.AccountsAdapter
import com.example.mycarapp.dto.Account
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountsManagementActivity : AppCompatActivity() {

    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var accountsRecyclerView: RecyclerView
    private lateinit var fabAddAccount: FloatingActionButton
    private lateinit var fabHelp: FloatingActionButton // DODAJ TĘ ZMIENNĄ

    private lateinit var emptyStateView: View
    private lateinit var accountsAdapter: AccountsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_accounts_management)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        setupListeners() // DODAJ WYWOŁANIE TEJ METODY
        setupSwipeToDelete()
        loadAccounts()
    }

    private fun setupUI() {
        accountsRecyclerView = findViewById(R.id.accounts_recycler_view)
        fabAddAccount = findViewById(R.id.fab_add_account)
        fabHelp = findViewById(R.id.fab_help) // ZNAJDŹ FAB POMOCY
        emptyStateView = findViewById(R.id.empty_state_view)

        accountsRecyclerView.layoutManager = LinearLayoutManager(this)
        accountsAdapter = AccountsAdapter(
            emptyList(),
            onAccountClick = { account -> onAccountSelected(account) },
            onAccountDelete = { account -> showDeleteConfirmation(account) },
            onSetAsDefault = { account -> setAsDefaultAccount(account) }
        )
        accountsRecyclerView.adapter = accountsAdapter
    }


    private fun showInstructionsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Instrukcja")
            .setMessage("• Kliknij konto, aby je wybrać\n• Przytrzymaj konto, aby ustawić je jako domyślne\n• Domyślne konto ma niebieskie tło")
            .setPositiveButton("Rozumiem", null)
            .show()
    }

    private fun setupListeners() {
        fabAddAccount.setOnClickListener {
            openAddAccountActivity()
        }

        fabHelp.setOnClickListener {
            showInstructionsDialog()
        }
    }

    private fun setAsDefaultAccount(account: Account) {
        lifecycleScope.launch {
            try {
                configManager.setAsDefaultAccount(account.id)
                Toast.makeText(
                    this@AccountsManagementActivity,
                    "Ustawiono jako konto domyślne",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@AccountsManagementActivity,
                    "Błąd podczas ustawiania konta domyślnego",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // Nie obsługujemy przeciągania
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val account = accountsAdapter.getAccountAtPosition(position)
                account?.let { showDeleteConfirmation(it) }
            }
        })

        itemTouchHelper.attachToRecyclerView(accountsRecyclerView)
    }

    private fun showDeleteConfirmation(account: Account) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Usuń konto")
            .setMessage("Czy na pewno chcesz usunąć konto ${account.username}@${account.serverUrl}?")
            .setPositiveButton("Usuń") { dialog, which ->
                deleteAccount(account)
            }
            .setNegativeButton("Anuluj") { dialog, which ->
                accountsAdapter.notifyDataSetChanged() // Odśwież aby przywrócić przesunięty element
            }
            .setCancelable(false)
            .show()
    }

    private fun deleteAccount(account: Account) {
        lifecycleScope.launch {
            try {
                configManager.deleteAccount(account.id)
                Toast.makeText(
                    this@AccountsManagementActivity,
                    "Konto usunięte",
                    Toast.LENGTH_SHORT
                ).show()

                // Automatycznie odświeży się przez Flow
            } catch (e: Exception) {
                Toast.makeText(
                    this@AccountsManagementActivity,
                    "Błąd podczas usuwania konta",
                    Toast.LENGTH_SHORT
                ).show()
                accountsAdapter.notifyDataSetChanged() // Przywróć widok jeśli błąd
            }
        }
    }

    private fun loadAccounts() {
        lifecycleScope.launch {
            try {
                configManager.getAllAccounts().collectLatest { accounts ->
                    if (accounts.isNotEmpty()) {
                        showAccountsList(accounts)
                    } else {
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
                showEmptyState()
            }
        }
    }

    private fun showAccountsList(accounts: List<Account>) {
        accountsRecyclerView.visibility = View.VISIBLE
        emptyStateView.visibility = View.GONE
        accountsAdapter.updateAccounts(accounts)
    }

    private fun showEmptyState() {
        accountsRecyclerView.visibility = View.GONE
        emptyStateView.visibility = View.VISIBLE
    }

    private fun onAccountSelected(account: Account) {
        lifecycleScope.launch {
            configManager.setActiveAccount(account.id)
            finish() // Wróć do AlbumsActivity
        }
    }

    private fun openAddAccountActivity() {
        val intent = Intent(this, AddAccountActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadAccounts()
    }
}