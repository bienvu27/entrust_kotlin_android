/**
 * Copyright (c) 2011 Entrust, Inc. All rights reserved.
 * Use is subject to the terms of the accompanying license agreement.
 * Entrust Confidential.
 */
package com.entrust.identityGuard.mobile.sdk.example

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.util.Base64
import com.entrust.identityGuard.mobile.sdk.Identity
import com.entrust.identityGuard.mobile.sdk.PlatformDelegate
import com.entrust.identityGuard.mobile.sdk.exception.EncryptionRequiredException
import org.json.JSONObject
import org.tinylog.Logger
import java.util.*

/**
 * Utility class that provides methods required by each Activity
 */
object Util {
    private const val IDENTITY = "Identity"
    private const val REGISTRATION_URL = "IdentityRegistrationURL"
    /**
     * Return the identity we stored
     *
     * @return The identity we stored
     */
    /**
     * Store the Identity created so it can be shared across multiple Activities.
     * Normally, this would go into some form of persistent storage, but with this
     * simple example, we just record the identity in memory.
     *
     * @param identity The Identity to store
     */
    var identity: Identity? = null
    var address: String? = null

    /**
     * Generate and display error dialog with the given message and an OK button
     *
     * @param context context in which to display the dialog
     * @param msg     The message to display.
     * @return The dialog that was created.
     */
    fun showErrorDialog(context: Context, msg: String?): Dialog {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(msg)
            .setTitle(R.string.title_error)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .setPositiveButton(
                context.getString(R.string.label_OK)
            ) { dialog, which -> // Pop down the dialog
                dialog.cancel()
            }
        return builder.show()
    }

    /**
     * Generate and display info dialog with the given message and an OK button
     *
     * @param context context in which to display the dialog
     * @param msg     The message to display.
     * @return The dialog that was created.
     */
    fun showInfoDialog(context: Context, msg: String?): Dialog {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(msg)
            .setTitle(R.string.title_error)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setCancelable(false)
            .setPositiveButton(
                context.getString(R.string.label_OK)
            ) { dialog, which -> // Pop down the dialog
                dialog.cancel()
            }
        return builder.show()
    }

    /**
     * Generate and display info dialog with the given message and an OK button
     *
     * @param context context in which to display the dialog
     * @param msg     The message to display.
     * @return The dialog that was created.
     */
    fun showInfoDialog(
        context: Context,
        msg: String?,
        listener: DialogInterface.OnClickListener?
    ): Dialog {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(msg)
            .setTitle(R.string.title_success)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.label_OK), listener)
        return builder.show()
    }

    /**
     * Generate and display a confirmation dialog with the given message and Yes/No buttons.
     *
     * @param context  Context in which to display the dialog
     * @param msg      The message to display
     * @param listener The Listener to call when either of the Yes/No buttons is invoked.
     * @return The dialog that was created
     */
    fun showConfirmationDialog(
        context: Context,
        msg: String?,
        listener: DialogInterface.OnClickListener?
    ): Dialog {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(msg)
            .setTitle(R.string.title_confirm)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(context.getString(R.string.label_yes), listener)
            .setNegativeButton(context.getString(R.string.label_no), listener)
            .setCancelable(false)
        return builder.show()
    }

    /**
     * Save the Encrypted Identity, RegistrationURL and DeviceAPIVersion in saved preferences.
     */
    fun saveIdentityInformation(context: Context) {
        try {
            val sharedPreferences = context.getSharedPreferences(IDENTITY, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            val jsonIdentity = identity!!.toJSON()
            val encryptedIdentity =
                PlatformDelegate.encryptData(jsonIdentity.toString().toByteArray())
            editor.putString(IDENTITY, Base64.encodeToString(encryptedIdentity, Base64.DEFAULT))
            editor.putString(
                REGISTRATION_URL,
                address
            )
            editor.apply()
        } catch (e: Exception) {
            Logger.error(e, "Error saving identity info")
        }
    }
    // < 3.5.3
    /*public static boolean extractIdentityInformation(Context context){
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences(IDENTITY, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            String identityString = sharedPreferences.getString(IDENTITY, "");
            String identityAddress = sharedPreferences.getString(REGISTRATION_URL, "");
            if (!identityString.equals("")) {
                byte[] decodeFromBase64 = Base64.decode(identityString, Base64.DEFAULT);
                byte[] decryptedIdentity = PlatformDelegate.decryptData(context, decodeFromBase64);
                Identity identity = new Identity(new JSONObject(new String(decryptedIdentity)));
                setIdentity(identity);
                setAddress(identityAddress);
                return true;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }*/
    // >=3.5.3
    /**
     * Extract the saved Encrypted Identity, RegistrationURL and DeviceAPIVersion from saved preferences.
     */
    fun extractIdentityInformation(context: Context): Boolean {
        try {
            val sharedPreferences = context.getSharedPreferences(IDENTITY, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            val identityString = sharedPreferences.getString(IDENTITY, "")
            Logger.info("old identity string is {}", identityString)
            val identityAddress = sharedPreferences.getString(REGISTRATION_URL, "")
            if (identityString != "") {
                val base64EncryptData = Base64.decode(identityString, Base64.DEFAULT)
                var decryptedIdentity: ByteArray? = null
                try {
                    decryptedIdentity = PlatformDelegate.decryptData(base64EncryptData)
                } catch (e: EncryptionRequiredException) {
                    Logger.error(e, "Error decrypting data")
                }
                val identity = Identity(
                    JSONObject(
                        String(
                            decryptedIdentity!!
                        )
                    )
                )
                Util.identity = identity
                address = identityAddress
                return true
            }
        } catch (e: Exception) {
            Logger.error("Error extracting identity info", "")
        }
        return false
    }// To register with the transaction component just
    // requires a non-null identifier. A real identifier -
    // such as a device PIN on BlackBerry or the registration_id
    // returned from registering an app for notifications on
    // Android - is required for the advanced notification
    // functionality. The random identifier used here is okay
    // for applications not using notifications.
    // Make sure the value is positive.
    /**
     * Return a unique identifier for the device (see comments inline below).
     * It is the combination of an Identity's instance ID and device ID that
     * has to be unique to register for transaction verification, and the
     * instance ID already has 64 bits of random.
     *
     * @return An identifier for the device.
     */
    val deviceId: String
        get() {
            // To register with the transaction component just
            // requires a non-null identifier. A real identifier -
            // such as a device PIN on BlackBerry or the registration_id
            // returned from registering an app for notifications on
            // Android - is required for the advanced notification
            // functionality. The random identifier used here is okay
            // for applications not using notifications.
            var randomVal = Random().nextInt()
            // Make sure the value is positive.
            randomVal = randomVal and 0x7FFFFFFF
            return randomVal.toString()
        }
}