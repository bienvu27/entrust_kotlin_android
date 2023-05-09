/**
 * Copyright (c) 2014 Entrust, Inc. All rights reserved.
 * Use is subject to the terms of the accompanying license agreement.
 * Entrust Confidential.
 */
package com.entrust.identityGuard.mobile.sdk.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.entrust.identityGuard.mobile.sdk.*
import com.entrust.identityGuard.mobile.sdk.example.PasswordInputDialogFragment.InputDialogListener
import com.entrust.identityGuard.mobile.sdk.example.Util.deviceId
import com.entrust.identityGuard.mobile.sdk.example.Util.extractIdentityInformation
import com.entrust.identityGuard.mobile.sdk.example.Util.showErrorDialog
import com.entrust.identityGuard.mobile.sdk.exception.BadBase64EncodingException
import com.entrust.identityGuard.mobile.sdk.exception.IdentityGuardMobileException
import com.entrust.identityGuard.mobile.sdk.exception.InvalidLaunchLinkException
import com.entrust.identityGuard.mobile.sdk.exception.MacMismatchException
import org.tinylog.Logger

/**
 * Activity used to add a new soft token identity.
 * Serial number and activation code are collected from the user.
 */
class AddIdentity : Activity(), InputDialogListener {
    private var mSerialNumber: EditText? = null
    private var mActivationCode: EditText? = null
    private var activateParams: SecureOfflineActivationUrlParams? = null

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_identity)
        Logger.info("Is device rooted? {}", PlatformDelegate.isDeviceRooted())
        mSerialNumber = findViewById<View>(R.id.serialNum) as EditText
        mActivationCode = findViewById<View>(R.id.actCode) as EditText
        val okButton = findViewById<View>(R.id.ok) as Button
        if (checkIdentityExistOrNot(this@AddIdentity)) {
            val intent = Intent(this@AddIdentity, SecurityCode::class.java)
            intent.putExtra(SecurityCode.IS_IDENTITY_SAVED, true)
            startActivity(intent)
            finish()
        }

        okButton.setOnClickListener {
            // Validate both the serial number and the activation code.
            val serialNumber = mSerialNumber!!.text.toString()
            val activationCode = mActivationCode!!.text.toString()
            if (validateSerialNumber(serialNumber) && validateActivationCode(activationCode)) {
                // Serial number and activation code are good: Generate an Identity from the IdentityProvider factory class
                try {
                    val identity: Identity =
                        IdentityProvider.generate(
                            DEVICE_ID,
                            serialNumber, activationCode
                        )
                    println(identity)
                    // Store our identity so it can be accessed by other activities
                    Util.identity = identity
                    // Determine whether the soft token identity requires a PIN to protect it.
                    if (identity.isPINRequired) {
                        // Our soft token identity requires a PIN
                        startActivity(Intent(this@AddIdentity, EstablishPIN::class.java))
                    } else {
                        // Go directly to showing the user the registration code.
                        startActivity(Intent(this@AddIdentity, RegistrationCode::class.java))
                    }
                    finish()
                } catch (e: Exception) {
                    Logger.error("Error generating identity", "")
                    // Since we have validated beforehand, this should not happen.
                    showErrorDialog(
                        this@AddIdentity,
                        getString(R.string.error_createFailure)
                    )
                }
            }
        }
        val cancelButton = findViewById<View>(R.id.cancel) as Button
        cancelButton.setOnClickListener { finish() }
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
            showErrorDialog(this, getString(R.string.error_badSerialNum))
            mSerialNumber!!.requestFocus()
            false
        }
    }

    private fun checkIdentityExistOrNot(context: Context): Boolean {
        return extractIdentityInformation(context)
    }

    /**
     * Validate the provided soft token activation code.
     *
     * @param actcode The activation code that needs validating.
     * @return true if the activation code is valid, false otherwise.
     */
    private fun validateActivationCode(actcode: String): Boolean {
        return try {
            IdentityProvider.validateActivationCode(actcode)
            true
        } catch (e: Exception) {
            // The activation code was not correct; set focus back to the
            // field where it was entered and show an alert dialog.
            showErrorDialog(this, getString(R.string.error_badActCode))
            mActivationCode!!.requestFocus()
            false
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

                                // The QR code is for a secure offline soft token identity
                                // activation.
                                if (params is SecureOfflineActivationUrlParams) {
                                    activateParams = params
                                    promptForPassword()
                                }
                            } catch (e: InvalidLaunchLinkException) {
                                showErrorDialog(
                                    this,
                                    getString(R.string.scan_qr_code_unknown_error)
                                )
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
        showErrorDialog(this, getString(R.string.scan_qr_code_error))
    }

    /**
     * Prompt the user to enter the password to be used to decrypt the parameters used for
     * activating the token via QR code.
     */
    private fun promptForPassword() {
        val fragmentManager = fragmentManager
        val pwdDialog = PasswordInputDialogFragment()
        val arguments = Bundle()
        arguments.putInt("title", R.string.secure_activation_password_prompt_title)
        arguments.putString("message", getString(R.string.secure_activation_password_prompt_text))
        pwdDialog.arguments = arguments
        pwdDialog.show(fragmentManager, getString(R.string.secure_activation_password_prompt_title))
    }

    override fun onFinishedDialog(password: String?) {
        if (password == null || password.trim() == "") {
            return
        }
        try {
            // Decrypt the parameters required for activation.
            val params = activateParams!!.decryptUsingPassword(password)
            mSerialNumber!!.setText(params.serialNumber)
            mSerialNumber!!.isEnabled = false
            mActivationCode!!.setText(params.activationCode)
            mActivationCode!!.isEnabled = false
        } catch (e: MacMismatchException) {
            showErrorDialog(this, getString(R.string.scan_qr_code_tampered_error_msg))
        } catch (e: BadBase64EncodingException) {
            displayQrScanError()
        } catch (e: IdentityGuardMobileException) {
            displayQrScanError()
        }
    }

    companion object {
        // Used when registering for transaction verification.  This can be null
        // if transaction verification is not required.
        private val DEVICE_ID = deviceId
        private const val SCAN_REQUEST_CODE = 0
        private const val SCAN_MODE = "SCAN_MODE"
        private const val QR_CODE_MODE = "QR_CODE_MODE"
        private const val SCAN_INTENT = "com.google.zxing.client.android.SCAN"
        private const val PLAY_STORE_URI = "market://details?id=com.google.zxing.client.android"
        private const val SCAN_RESULT = "SCAN_RESULT"
        private const val SCAN_RESULT_FORMAT = "SCAN_RESULT_FORMAT"
        private const val QR_CODE_FORMAT = "QR_CODE"
    }
}