/**
 * Copyright (c) 2011 Entrust, Inc. All rights reserved.
 * Use is subject to the terms of the accompanying license agreement.
 * Entrust Confidential.
 */
package com.entrust.identityGuard.mobile.sdk.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.entrust.identityGuard.mobile.sdk.example.Util.showErrorDialog

/**
 * Activity used to establish a PIN for the soft token identity.
 */
class EstablishPIN : Activity() {
    private var mPin: EditText? = null
    private var mPinConfirm: EditText? = null
    private var onlineActivation = false

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.establish_pin)
        val intent = intent
        val extras = intent.extras
        onlineActivation = extras != null && extras.getBoolean("online")
        val intro = getString(R.string.intro_PIN, PIN_LENGTH)
        (findViewById<View>(R.id.pinIntro) as TextView).text = intro
        mPin = findViewById<View>(R.id.pin) as EditText
        mPinConfirm = findViewById<View>(R.id.pinConfirm) as EditText
        val okButton = findViewById<View>(R.id.ok) as Button
        okButton.setOnClickListener {
            // Get the values the user entered for the PIN and its confirmation
            val pinValue = mPin!!.text.toString()
            val pinValueConfirm = mPinConfirm!!.text.toString()
            // In order to move to the next step, both the PIN and Confirm PIN fields must be non-empty
            // and they must have the same value.
            if (pinValue.length > 0 && pinValueConfirm.length > 0 && pinValue == pinValueConfirm) {
                // Move on to display the registration code.
                if (onlineActivation) {
                    startActivity(Intent(this@EstablishPIN, SecurityCode::class.java))
                } else {
                    startActivity(Intent(this@EstablishPIN, RegistrationCode::class.java))
                }
                finish()
            } else {
                // One or both of the fields are empty or their values don't match.
                // Clear out both fields, set focus back to the PIN entry field, and
                // announce the problem with an error dialog.
                mPin!!.setText("")
                mPinConfirm!!.setText("")
                mPin!!.requestFocus()
                showErrorDialog(
                    this@EstablishPIN,
                    getString(R.string.error_mismatchPIN)
                )
            }
        }
    }

    companion object {
        // All soft token PINs are the following length.
        private const val PIN_LENGTH = 4
    }
}