/**
 * Copyright (c) 2011 Entrust, Inc. All rights reserved.
 * Use is subject to the terms of the accompanying license agreement.
 * Entrust Confidential.
 */
package com.entrust.identityGuard.mobile.sdk.example

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

/**
 * Activity that presents the registration code. The end-user must
 * enter this value back into their self-service web page or provide it
 * to an administrator (or help desk person) to complete the soft token
 * activation process.
 */
class RegistrationCode : Activity() {
    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.registration_code)
        // Display an explanation of what needs to be done with the registration code
        // plus the code itself.
        val regCode = findViewById<View>(R.id.regCode) as TextView
        regCode.text = Util.identity?.registrationCode
        val okButton = findViewById<View>(R.id.ok) as Button
        okButton.setOnClickListener {
            // Ask the user if they have used the registration code.
            Util.showConfirmationDialog(this@RegistrationCode, getString(R.string.confirm_regCodeUse)) { dialog, which ->
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // If the answer is yes, clear out the registration code and move on to the security code.
                    Util.identity?.clearRegistrationCode()
                    startActivity(Intent(this@RegistrationCode, SecurityCode::class.java))
                    finish()
                } else {
                    // Return back to the Activity screen
                    dialog.cancel()
                }
            }
        }
    }
}