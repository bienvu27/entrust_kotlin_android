package com.entrust.identityGuard.mobile.sdk.example

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.entrust.identityGuard.mobile.sdk.*
import com.entrust.identityGuard.mobile.sdk.exception.IdentityGuardMobileException
import org.tinylog.Logger

class TransactionProcess : Activity() {
    private var mTransaction: Transaction? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.transaction)
        val intent = intent
        mTransaction = intent.extras!!["txn"] as Transaction?

        // Display transaction summary if one is provided.
        val summary = findViewById<View>(R.id.summary) as TextView
        if (mTransaction!!.summary != null) {
            summary.text = mTransaction!!.summary
        } else {
            summary.visibility = View.GONE
        }

        // Display the transaction details if any are provided.
        val table = findViewById<View>(R.id.details_table) as TableLayout
        if (mTransaction!!.details != null) {
            addTransactionDetails(table, mTransaction)
        } else {
            table.visibility = View.GONE
        }
        val concern = findViewById<View>(R.id.concern) as Button
        concern.setOnClickListener {
            try {
                mTransaction!!.transactionResponse = TransactionResponse.CONCERN
                if (mTransaction!!.transactionMode.toString() == TransactionMode.ONLINE.toString()) {
                    val response = OnlineTransactionResponse(this@TransactionProcess,
                            TransactionProvider.getConfirmationCode(Util.identity, mTransaction))
                    response.execute()
                } else {
                    finish()
                }
            } catch (e: Exception) {
                Logger.error(e,
                        "An error occurred while sending a transaction response")
                Util.showErrorDialog(this@TransactionProcess,
                        getString(R.string.error_generating_confirmation_code))
            }
        }
        val cancel = findViewById<View>(R.id.cancel) as Button
        cancel.setOnClickListener {
            try {
                mTransaction!!.transactionResponse = TransactionResponse.CANCEL
                if (mTransaction!!.transactionMode.toString() == TransactionMode.ONLINE.toString()) {
                    val response = OnlineTransactionResponse(this@TransactionProcess,
                            TransactionProvider.getConfirmationCode(Util.identity, mTransaction))
                    response.execute()
                } else {
                    finish()
                }
            } catch (e: Exception) {
                Logger.error(e, "An error occurred while sending a transaction response")
                Util.showErrorDialog(this@TransactionProcess,
                        getString(R.string.error_generating_confirmation_code))
            }
        }
        val confirm = findViewById<View>(R.id.confirm) as Button
        confirm.setOnClickListener {
            try {
                mTransaction!!.transactionResponse = TransactionResponse.CONFIRM
                if (mTransaction!!.transactionMode.toString() == TransactionMode.ONLINE.toString()) {
                    val response = OnlineTransactionResponse(this@TransactionProcess,
                            TransactionProvider.getConfirmationCode(Util.identity, mTransaction))
                    response.execute()
                } else {
                    val intent = Intent(this@TransactionProcess, ClassicTransaction::class.java)
                    intent.putExtra("txn", mTransaction)
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Logger.error(e, "An error occurred while sending a transaction response")
                Util.showErrorDialog(this@TransactionProcess,
                        getString(R.string.error_generating_confirmation_code))
            }
        }
    }

    private fun addTransactionDetails(table: TableLayout, transaction: Transaction?) {
        val details = transaction!!.details
        for (i in details.indices) {
            addDetailRow(table, details[i])
        }
    }

    private fun addDetailRow(table: TableLayout, detail: TransactionDetail) {
        val inflater = layoutInflater
        val row = inflater.inflate(R.layout.transaction_details_row, table, false) as TableRow
        val nameView = row.findViewById<View>(R.id.detail) as TextView
        nameView.text = detail.detail
        val valueView = row.findViewById<View>(R.id.value) as TextView
        valueView.text = detail.value
        table.addView(row)
    }

    private fun completeOnlineTransaction(result: Boolean, transaction: Transaction?) {
        if (result) {
            Util.showInfoDialog(this, getString(R.string.transaction_success,
                    mTransaction!!.transactionResponse.toString())
            ) { dialog, which ->
                dialog.dismiss()
                dialog.cancel()
                finish()
            }
        } else {
            Util.showErrorDialog(this, getString(R.string.transaction_failed))
        }
    }

    private fun onlineTransactionFailed(e: Exception?) {
        if (e != null) {
            Util.showErrorDialog(this, e.message)
        } else {
            Util.showErrorDialog(this, getString(R.string.transaction_failed))
        }
    }

    private inner class OnlineTransactionResponse(private val mContext: Context, private val mResponse: String) : AsyncTask<Void?, Void?, Boolean>() {
        override fun onPostExecute(result: Boolean) {
            var result: Boolean? = result
            super.onPostExecute(result)
            stopDialog()

            // Give the result a value if null
            result = result ?: false
            if (mTransactionFailedException != null) {
                onlineTransactionFailed(mTransactionFailedException)
            } else {
                completeOnlineTransaction(result, mTransaction)
            }
        }

        override fun doInBackground(vararg p0: Void?): Boolean {
            mTransactionFailedException = try {
                val tp = TransactionProvider(Util.address)
                return tp.authenticateTransaction(PlatformDelegate.getCommCallback(),
                        Util.identity, mTransaction, mResponse)
            } catch (e: IdentityGuardMobileException) {
                Logger.error(e, "Error authenticating transaction")
                e
            } catch (e: Exception) {
                Logger.error(e,
                        "Error authenticating transaction")
                e
            }
            return false
        }

        override fun onPreExecute() {
            super.onPreExecute()
            createDialog(mContext.getString(R.string.dialog_responding))
        }

        /**
         * Creates a new `ProgressDialog` object displaying the provided message.
         *
         * @param message The message to display.
         */
        private fun createDialog(message: String) {
            if (mDialog != null) {
                mDialog = null
            }
            mDialog = ProgressDialog(mContext)
            mDialog!!.setMessage(message)
            mDialog!!.show()
        }

        /**
         * Stop the current `ProgressDialog`.
         */
        private fun stopDialog() {
            if (mDialog != null) {
                mDialog!!.dismiss()
                mDialog!!.cancel()
                mDialog = null
            }
        }

        private var mTransactionFailedException: Exception? = null
        private var mDialog: ProgressDialog? = null
    }
}