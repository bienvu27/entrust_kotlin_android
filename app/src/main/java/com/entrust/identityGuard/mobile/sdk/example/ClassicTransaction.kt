package com.entrust.identityGuard.mobile.sdk.example

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.entrust.identityGuard.mobile.sdk.Transaction
import com.entrust.identityGuard.mobile.sdk.TransactionProvider
import org.tinylog.Logger

class ClassicTransaction : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.classic_transaction)
        val intent = intent
        val transaction = intent.extras!!["txn"] as Transaction?
        val confirmCode = findViewById<View>(R.id.confirm_code) as TextView
        try {
            confirmCode.text = TransactionProvider.getConfirmationCode(Util.identity, transaction)
        } catch (e: Exception) {
            Logger.error(e, TAG, "Error getting confirmation code")
        }
        val done = findViewById<View>(R.id.ok) as Button
        done.setOnClickListener { finish() }
    }

    override fun onBackPressed() {
        finish()
    }

    companion object {
        private val TAG = ClassicTransaction::class.java.simpleName
    }
}