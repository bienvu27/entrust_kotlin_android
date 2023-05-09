package com.entrust.identityGuard.mobile.sdk.example

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.entrust.identityGuard.mobile.sdk.*
import com.entrust.identityGuard.mobile.sdk.exception.IdentityGuardMobileException
import org.tinylog.Logger

/**
 * Activity which handles the online activation scenario introduced as part of the 2.0 release
 * of the IdentityGuard Mobile SDK.  This activity is launched from a user clicking an
 * app-specific link.  The link contains the registration URL, registration password and the
 * serial number of the token required to activate the new soft token.
 *
 * @since 2.0
 */
class OnlineActivation : Activity() {
    private var mSerialNumber: EditText? = null
    private var mAddress: EditText? = null
    private lateinit var mRegistrationPassword: String

    /* (non-Javadoc)
     * @see android.app.Activity#onCreate(Bundle)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Did user arrive via app-specific link
        val intent = intent
        val intentAction = intent.action
        if (intentAction != null && intentAction == Intent.ACTION_VIEW) {
            setContentView(R.layout.online_activation)
            val actionBar = actionBar
            actionBar!!.setTitle(R.string.title_addIdentity)
            mSerialNumber = findViewById<View>(R.id.serialNum) as EditText
            mAddress = findViewById<View>(R.id.address) as EditText
            parseLaunchUrlParameters(intent)
            establishButtons()
        } else {
            // We should only be able to get to this activity via app-specific link.  If we
            // got here some other way, launch the classic add identity activity.
            startActivity(Intent(this, AddIdentity::class.java))
            finish()
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onBackPressed()
     */
    override fun onBackPressed() {
        finish()
    }

    /**
     * Parses the parameters out of the app-specific link that launched the app and displays the
     * registration URL and token serial serial numbers in the appropriate fields.
     *
     * @param intent the intent.
     */
    private fun parseLaunchUrlParameters(intent: Intent) {
        try {
            val linkParams = PlatformDelegate.parseLaunchUrl(intent)
            if (linkParams is ActivationLaunchUrlParams) {
                val address = getAddress(
                    linkParams
                        .registrationUrl
                )
                mSerialNumber!!.setText(linkParams.serialNumber)
                mAddress!!.setText(address)
                mRegistrationPassword = linkParams
                    .registrationPassword
            }
        } catch (e: IdentityGuardMobileException) {
            Logger.error(TAG, "Error parsing launch url")
            Util.showErrorDialog(this, getString(R.string.error_badSerialNum))
        }
    }

    /**
     * Set the actions to take on click with the OK and cancel buttons.
     */
    private fun establishButtons() {
        val cancel = findViewById<View>(R.id.cancel) as Button
        cancel.setOnClickListener { finish() }
        val ok = findViewById<View>(R.id.ok) as Button
        ok.setOnClickListener {
            val serialNumber = mSerialNumber!!.text.toString().trim { it <= ' ' }
            // Validate the serial number.
            if (validateSerialNumber(serialNumber)) {
                val address = mAddress!!.text.toString().trim { it <= ' ' }
                val cri: CreateRegisterIdentity =
                    CreateRegisterIdentity(
                        this@OnlineActivation,
                        Util.deviceId,
                        mRegistrationPassword,
                        address,
                        serialNumber
                    )
                cri.execute()
            }
        }
    }

    /**
     * Gets the registration URL and appends the `https://` prefix if it is missing.
     *
     * @param address The registration URL
     * @return the complete address to use as the registration URL
     */
    private fun getAddress(address: String): String {
        return if (address.startsWith("https://")) address else "https://$address"
    }

    /**
     * Validate the provided soft token serial number.
     *
     * @param sernum The serial number that needs validating.
     * @return true if the serial number is valid, false otherwise.
     */
    private fun validateSerialNumber(sernum: String): Boolean {
        return try {
            IdentityProvider.validateSerialNumber(sernum)
            true
        } catch (e: Exception) {
            // The serial number was not correct; set focus back to the
            // field where it was entered and show an alert dialog.
            Util.showErrorDialog(this, getString(R.string.error_badSerialNum))
            mSerialNumber!!.requestFocus()
            false
        }
    }

    /**
     * Actions to take upon successful online activation and registration.
     *
     * @param result The created identity.
     */
    private fun completeOnlineActivation(result: Identity) {

        // Store our identity so it can be accessed by other activities
        Util.identity = result

        //Store the address so that we can perform online tranactions
        Util.address = mAddress!!.text.toString().trim { it <= ' ' }

        // Determine whether the soft token identity requires a PIN to protect it.
        if (result.isPINRequired) {
            // Our soft token identity requires a PIN
            val intent = Intent(this, EstablishPIN::class.java)
            intent.putExtra("online", true)
            startActivity(intent)
        } else {
            // Go directly to showing the user the security code.
            startActivity(Intent(this, SecurityCode::class.java))
        }
        finish()
    }

    /**
     * Display an error to the user indicating the reason for the failed online activation.
     *
     * @param e The exception.
     */
    private fun failedOnlineActivation(e: Exception?) {
        Util.showErrorDialog(this, e!!.message)
    }

    /**
     * An asynchronous task to perform the communication with the IdentityGuard Self-Service
     * Transaction Component to complete the online activation of a token.
     */
    private inner class CreateRegisterIdentity(
        private val mContext: Context, private var mDeviceId: String?, private val mRegPwd: String,
        private val mTransactionAddress: String, private val mSerNum: String
    ) : AsyncTask<Void?, Void?, Identity?>() {
        private var mDialog: ProgressDialog? = null
        private var activationFailException: Exception? = null

        /* (non-Javadoc)
          * @see android.os.AsyncTask#doInBackground()
          */
        override fun doInBackground(vararg p0: Void?): Identity? {
            try {
                PlatformDelegate.setApplicationId("otp.test")
                val tp = TransactionProvider(mTransactionAddress)
                tp.setContext(mContext)

                // For the purpose of this sample app, we will disable notifications for
                // transaction notifications.
                val supportsNotification = false

                // For the purpose of this sample app, we will enable transactions.
                val supportsTransactions = true

                // For the purpose of this sample app, we will enable online transactions.
                val supportsOnlineTransactions = true

                // For the purpose of this sample app, we will enable offline transactions.
                val supportsOfflineTransactions = true

                // The device ID field is used to send notifications to the device.
                // For Android devices, this value corresponds to the Google Cloud Messaging
                // (GCM) registration ID.
                if (mDeviceId == null) {
                    mDeviceId = Util.deviceId
                }
                return tp.createIdentityUsingRegPassword(
                    PlatformDelegate.getCommCallback(), mRegPwd, mSerNum, mDeviceId,
                    supportsNotification, supportsTransactions,
                    supportsOnlineTransactions, supportsOfflineTransactions
                )
            } catch (e: IdentityGuardMobileException) {
                Logger.error(
                    e,
                    TAG,
                    "Error registering identity"
                )
                activationFailException = e
            } catch (e: Exception) {
                Logger.error(
                    e, TAG,
                    "Error registering identity"
                )
                activationFailException = e
            }

            // An exception occurred during activation/registration, so return a null object for
            // the identity.
            return null
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute()
         */
        override fun onPostExecute(result: Identity?) {
            super.onPostExecute(result)
            stopDialog()
            if (result != null) {
                completeOnlineActivation(result)
            } else {
                failedOnlineActivation(activationFailException)
            }
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        override fun onPreExecute() {
            super.onPreExecute()
            createDialog(mContext.getString(R.string.dialog_registering))
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
    }

    companion object {
        private val TAG = OnlineActivation::class.java.simpleName
    }
}