/**
 * Copyright (c) 2011 Entrust, Inc. All rights reserved.
 * Use is subject to the terms of the accompanying license agreement.
 * Entrust Confidential.
 */
package com.entrust.identityGuard.mobile.sdk.example

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.entrust.identityGuard.mobile.sdk.*
import com.entrust.identityGuard.mobile.sdk.exception.IdentityGuardMobileException
import com.entrust.identityGuard.mobile.sdk.exception.InvalidLaunchLinkException
import com.entrust.identityGuard.mobile.sdk.exception.MacMismatchException
import org.tinylog.Logger
import java.util.*

/**
 * Activity that displays the ever changing security code (i.e., one-time password).
 */
class SecurityCode : Activity() {
    private var mCountdown: ProgressBar? = null
    private var mOTP: TextView? = null
    private var mTimer: Timer? = null
    private var recyclerView: RecyclerView? = null
    private val mHandler = Handler()
    private val mIdentity = Util.identity
    private var transactionClicked: TransactionClicked? = null
    private var pendingTransactions: ArrayList<Transaction>? = null
    private var pendingList: PendingList? = null
    private var context: Context? = null

    /**
     * Called when the activity is first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.security_code)
        context = this

        //If Identity tag exist in extras then create identity if not then save newly created Identity in Preference.
        if (!intent.hasExtra(IS_IDENTITY_SAVED)) {
            Util.saveIdentityInformation(applicationContext)
        }

        // Display the security code (OTP) value and a progress bar that represents
        // a countdown until a new security code needs to be generated (i.e., the
        // OTP's lifetime). Use the available IdentityProvider formatCode() helper method
        // to split the OTP into two with a separating hyphen.
        mOTP = findViewById<View>(R.id.otp) as TextView
        var otp: String? = ""
        for (i in 0 until mIdentity?.otpLength!!) {
            otp += "0"
        }
        try {
            otp = mIdentity.otp
        } catch (e: IdentityGuardMobileException) {
            Logger.error(e, "Failed to get the current OTP")
        } finally {
            mOTP?.text = IdentityProvider.formatCode(otp, mIdentity.otpLength / 2, '-')
        }
        mCountdown = findViewById<View>(R.id.countdown) as ProgressBar
        // Set up a fixed rate timer that counts down until a new security code should be generated.
        mTimer = Timer()
        recyclerView = findViewById<View>(R.id.PendingTransactionsList) as RecyclerView
        mTimer?.scheduleAtFixedRate(object : TimerTask() {
            private var ticks = OTP_VALIDITY_TIME

            /**
             * Starting after a second (1000ms) and for every second thereafter,
             * decrement the countdown timer by one, and if we're reached zero,
             * generate a new security code and reset our countdown display.
             */
            override fun run() {
                // Since we're changing the GUI, this should happen via a worker thread
                mHandler.post {
                    if (--ticks == 0) {
                        ticks = OTP_VALIDITY_TIME
                        var otp: String? = ""
                        for (i in 0 until mIdentity.otpLength) {
                            otp += "0"
                        }
                        try {
                            otp = mIdentity?.otp
                        } catch (e: IdentityGuardMobileException) {
                            Logger.error(e, "Failed to get the current OTP")
                        } finally {
                            mOTP?.text = IdentityProvider.formatCode(otp, mIdentity.otpLength / 2, '-')
                        }
                    }
                    mCountdown?.progress = ticks
                }
            }
        }, 1000, 1000)
        val newTransactions = findViewById<View>(R.id.ok) as Button
        newTransactions.setOnClickListener { // Check for new transaction's for the soft token identity.
            val txnTask = TransactionTask(this@SecurityCode)
            txnTask.execute()
        }
        val qrCodeButton = findViewById<View>(R.id.qr_code) as Button
        qrCodeButton.setOnClickListener {
            try {
                // Create a new intent to be handled by any app which supports the
                // action specified. If more than one app supporting the action exists on the
                // device, an app chooser will be presented to the user.
                val intent = Intent(SCAN_INTENT)
                intent.putExtra(SCAN_MODE, QR_CODE_MODE)

                // The device will switch to the chosen app to scan the QR code. When the code
                // has been scanned, the {@link #onActivityResult(int, int, Intent)} callback
                // will be called.
                startActivityForResult(intent, SCAN_REQUEST_CODE)
            } catch (e: Exception) {
                // No app exists supporting the given action. Redirect the user to the Play
                // Store to download an app which can scan QR codes.
                val playStoreUri = Uri.parse(PLAY_STORE_URI)
                val playStoreIntent = Intent(Intent.ACTION_VIEW, playStoreUri)
                startActivity(playStoreIntent)
            }
        }
        transactionClicked = object : TransactionClicked {
            override fun onClicked(transaction: Transaction?, position: Int) {
                handleTransaction(transaction)
                pendingTransactions = ArrayList()
                pendingList = PendingList(pendingTransactions!!, this@SecurityCode, transactionClicked)
                recyclerView?.adapter = pendingList
            }
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onBackPressed()
     */
    override fun onBackPressed() {
        finish()
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onDestroy()
     */
    override fun onDestroy() {
        super.onDestroy()
        // Cancel the timer before we quit.
        mTimer?.cancel()
    }

    /**
     * Actions to take upon successful transaction retrieval.
     *
     * @param result The created identity.
     */
    private fun handleTransaction(result: Transaction?) {
        if (result == null) {
            Util.showInfoDialog(this, getString(R.string.no_new_transactions_message))
        } else {
            val intent = Intent(this, TransactionProcess::class.java)
            intent.putExtra("txn", result)
            startActivity(intent)
        }
    }

    /**
     * Display an error to the user indicating the reason for the failed transaction retrieval.
     *
     * @param e The exception.
     */
    private fun failedTransactionRetrieval(e: Exception?) {
        if (e != null) {
            Util.showErrorDialog(this, e.message)
        } else {
            Util.showInfoDialog(this, getString(R.string.no_new_transactions_message))
        }
    }

    private inner class TransactionTask internal constructor(private val mContext: Context) : AsyncTask<Void?, Void?, ArrayList<Transaction>?>() {
        private var mDialog: ProgressDialog? = null
        private var transactionRetrieveFailException: Exception? = null

        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground()
         */

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute()
         */
        override fun onPostExecute(result: ArrayList<Transaction>?) {
            super.onPostExecute(result)
            stopDialog()
            pendingTransactions = result
            if (pendingTransactions != null) {
                if (pendingTransactions?.size == 1) {
                    handleTransaction(pendingTransactions!![0])
                } else {
                    pendingList = PendingList(pendingTransactions!!, mContext, transactionClicked)
                    recyclerView?.adapter = pendingList
                    recyclerView?.setHasFixedSize(true)
                    recyclerView?.layoutManager = LinearLayoutManager(mContext,
                            LinearLayoutManager.VERTICAL, false)
                }
            } else {
                pendingTransactions = ArrayList()
                pendingList = PendingList(pendingTransactions!!, mContext, transactionClicked)
                recyclerView?.adapter = pendingList
                failedTransactionRetrieval(transactionRetrieveFailException)
            }
        }

        override fun doInBackground(vararg p0: Void?): ArrayList<Transaction>? {
            transactionRetrieveFailException = try {
                // Create a new transaction provider based on the provider address.
//                val tp = TransactionProvider(Util.getAddress())
                val tp = TransactionProvider(Util.address)

                // Poll the transaction provider for new transactions.
                return tp.pollQueue(PlatformDelegate.getCommCallback(), mIdentity)
            } catch (e: IdentityGuardMobileException) {
                Logger.error(e, "Error polling for new transactions")
                e
            } catch (e: Exception) {
                Logger.error(e, "Error polling for new transactions")
                e
            }

            // An exception occurred during activation/registration, so return a null object for
            // the identity.
            return null
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        override fun onPreExecute() {
            super.onPreExecute()
            createDialog(mContext.getString(R.string.dialog_retrieving))
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
            mDialog?.setMessage(message)
            mDialog?.show()
        }

        /**
         * Stop the current `ProgressDialog`.
         */
        private fun stopDialog() {
            if (mDialog != null) {
                mDialog?.dismiss()
                mDialog?.cancel()
                mDialog = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {

        // Verify that the callback was made in response to the QR code scan request.
        if (requestCode == SCAN_REQUEST_CODE) {
            // Verify that the result was successful.
            if (resultCode == RESULT_OK) {
                val format = intent.getStringExtra(SCAN_RESULT_FORMAT)
                if (format == QR_CODE_FORMAT) {
                    // The result of the QR code scan.
                    val contents = intent.getStringExtra(SCAN_RESULT)
                    if (contents != null) {
                        val uriFromQrCode = Uri.parse(contents)
                        if (uriFromQrCode != null && uriFromQrCode.queryParameterNames != null) {
                            val qrIntent = Intent()
                            qrIntent.data = uriFromQrCode
                            try {
                                val params = PlatformDelegate.parseLaunchUrl(qrIntent)
                                if (params is OfflineTransactionUrlParams) {

                                    // The QR code is for an offline transaction.
                                    handleTransactionFromQrCode(params)
                                }
                            } catch (e: InvalidLaunchLinkException) {
                                Util.showErrorDialog(this,
                                        getString(R.string.scan_qr_code_unknown_error))
                            } catch (e: IdentityGuardMobileException) {
                                displayQrScanError()
                            }
                        } else {
                            displayQrScanError()
                        }
                    } else {
                        displayQrScanError()
                    }
                }
            } else if (resultCode == RESULT_CANCELED) {
                // TODO: Handle cancel case.
            }
        }
    }

    private fun displayQrScanError() {
        Util.showErrorDialog(this, getString(R.string.scan_qr_code_error))
    }

    /**
     * Handles the transaction returned from a successful QR code scan.
     *
     * @param txnParams The transaction parameters.
     */
    private fun handleTransactionFromQrCode(txnParams: OfflineTransactionUrlParams) {
        if (mIdentity?.serialNumber != txnParams.serialNumber) {
            Toast.makeText(this, "Transaction not intended for the current identity.",
                    Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // The transaction details are encrypted for a specific soft token identity and can
            // only be decrypted by that identity.
            val txn = mIdentity?.getOfflineTransactionFromUrlParams(txnParams)
            val intent = Intent(this, TransactionProcess::class.java)
            intent.putExtra("txn", txn)
            startActivity(intent)
        } catch (e: MacMismatchException) {
            Util.showErrorDialog(this, getString(R.string.scan_qr_code_tampered_error_msg))
        } catch (e: IdentityGuardMobileException) {
            displayQrScanError()
        }
    }

    interface TransactionClicked {
        fun onClicked(transaction: Transaction?, position: Int)
    }

    companion object {
        // The length of time (in seconds) the current security code
        // should be displayed before a new one is generated.
        private const val OTP_VALIDITY_TIME = 30
        private const val SCAN_REQUEST_CODE = 0
        private const val SCAN_MODE = "SCAN_MODE"
        private const val QR_CODE_MODE = "QR_CODE_MODE"
        private const val SCAN_INTENT = "com.google.zxing.client.android.SCAN"
        private const val PLAY_STORE_URI = "market://details?id=com.google.zxing.client.android"
        private const val SCAN_RESULT = "SCAN_RESULT"
        private const val SCAN_RESULT_FORMAT = "SCAN_RESULT_FORMAT"
        private const val QR_CODE_FORMAT = "QR_CODE"
        const val IS_IDENTITY_SAVED = "IdentitySaved"
    }
}