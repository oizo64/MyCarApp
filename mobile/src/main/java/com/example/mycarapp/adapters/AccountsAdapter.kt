package com.example.mycarapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mycarapp.R
import com.example.mycarapp.dto.Account

class AccountsAdapter(
    private val onAccountClick: (Account) -> Unit,
    private val onAccountDelete: (Account) -> Unit,
    private val onSetAsDefault: (Account) -> Unit
) : ListAdapter<Account, AccountsAdapter.AccountViewHolder>(AccountDiffCallback()) {

    class AccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val serverUrl: TextView = itemView.findViewById(R.id.server_url_text)
        val username: TextView = itemView.findViewById(R.id.username_text)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
        val defaultLabel: TextView = itemView.findViewById(R.id.default_label)
        val itemLayout: View = itemView.findViewById(R.id.account_item_layout)
        val textContentLayout: View = itemView.findViewById(R.id.text_content_layout)
    }

    private class AccountDiffCallback : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val account = getItem(position)
        holder.serverUrl.text = account.serverUrl
        holder.username.text = account.username

        // Ustaw tło dla konta domyślnego
        if (account.isDefault) {
            holder.itemLayout.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.blue_200)
            )
            holder.defaultLabel.visibility = View.VISIBLE
        } else {
            holder.itemLayout.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.transparent)
            )
            holder.defaultLabel.visibility = View.GONE
        }

        // KLIKNIĘCIE - wybór konta (tylko na obszarze tekstu)
        holder.textContentLayout.setOnClickListener {
            onAccountClick(account)
        }

        // DŁUGIE KLIKNIĘCIE - ustawienie jako domyślne (tylko na obszarze tekstu)
        holder.textContentLayout.setOnLongClickListener {
            onSetAsDefault(account)
            true
        }

        // PRZYCISK USUWANIA
        holder.deleteButton.setOnClickListener {
            onAccountDelete(account)
        }
    }

    fun getAccountAtPosition(position: Int): Account? {
        return if (position in 0 until itemCount) {
            getItem(position)
        } else {
            null
        }
    }
}
