/**
 * Copyright (c) 2014 Entrust, Inc. All rights reserved.
 * Use is subject to the terms of the accompanying license agreement.
 * Entrust Confidential.
 */
package com.entrust.identityGuard.mobile.sdk.example

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText

class PasswordInputDialogFragment : DialogFragment() {
    private var title = 0
    private var message: String? = null
    private var inputField: EditText? = null

    interface InputDialogListener {
        fun onFinishedDialog(inputText: String?)
    }

    override fun setArguments(arguments: Bundle) {
        title = arguments.getInt("title")
        message = arguments.getString("message")
    }

    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val inflater = activity.layoutInflater
        val view = inflater.inflate(R.layout.dialog_edit_text, null)
        inputField = view.findViewById<View>(R.id.dialog_inputText) as EditText
        inputField!!.transformationMethod = PasswordTransformationMethod.getInstance()
        return AlertDialog.Builder(activity)
            .setView(view)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                android.R.string.ok
            ) { dialog, which ->
                val activity = activity as InputDialogListener
                activity.onFinishedDialog(inputField!!.text.toString().trim { it <= ' ' })
                dialog.dismiss()
                dialog.cancel()
            }
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog, which ->
                inputField!!.setText("")
                val activity = activity as InputDialogListener
                activity.onFinishedDialog(null)
                dialog.dismiss()
                dialog.cancel()
            }.create()
    }
}