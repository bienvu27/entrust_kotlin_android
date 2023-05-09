package com.entrust.identityGuard.mobile.sdk.example

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.entrust.identityGuard.mobile.sdk.*
import com.entrust.identityGuard.mobile.sdk.example.SecurityCode.TransactionClicked

class PendingList(private val transactions: ArrayList<Transaction>, private val context: Context, private val clicked: TransactionClicked?) : RecyclerView.Adapter<PendingList.ViewHolder>() {
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        return ViewHolder(inflater.inflate(R.layout.transaction_item, viewGroup, false))
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        viewHolder.txnSummary.text = transactions[position].summary
        viewHolder.txnSummary.setOnClickListener { clicked!!.onClicked(transactions[position], position) }
    }

    override fun getItemCount(): Int {
        return transactions.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txnSummary: TextView

        init {
            txnSummary = itemView.findViewById<View>(R.id.TxnSummary) as TextView
        }
    }
}