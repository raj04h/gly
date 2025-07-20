package com.adbase.gly.user_logic

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.adbase.gly.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class loginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnVerifyEmail: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnSignUp: Button
    private lateinit var btnSignIn: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var cardView: CardView

    // Simple loading indicator
    private lateinit var progress: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Firebase instances
        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        // Bind views
        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        btnVerifyEmail   = findViewById(R.id.btnVerifyEmail)
        tvStatus         = findViewById(R.id.tvStatus)
        btnSignUp        = findViewById(R.id.btnSignUp)
        btnSignIn        = findViewById(R.id.btnSignIn)
        tvForgotPassword = findViewById(R.id.btnForgotPassword)
        cardView         = findViewById(R.id.cardView)

        progress = ProgressDialog(this).apply {
            setMessage("Please wait…")
            setCancelable(false)
        }

        // Button handlers
        btnVerifyEmail.setOnClickListener { resendVerificationEmail() }
        btnSignUp     .setOnClickListener { handleSignUp() }
        btnSignIn     .setOnClickListener { handleSignIn() }
        tvForgotPassword.setOnClickListener { handlePasswordReset() }
    }

    override fun onStart() {
        super.onStart()
        // If already signed in & verified, go straight to content
        auth.currentUser?.takeIf { it.isEmailVerified }?.let {
            navigateToContent(it)
        }
    }

    private fun handleSignUp() {
        val email = etEmail.text.toString().trim()
        val pass  = etPassword.text.toString().trim()
        if (!isValidEmail(email)) {
            etEmail.error = "Enter a valid email"
            return
        }
        if (pass.length < 6) {
            etPassword.error = "Password must be ≥6 chars"
            return
        }
        progress.show()
        disableForm()
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                progress.dismiss()
                if (task.isSuccessful) {
                    sendInitialVerification()
                } else {
                    showError("Sign‑up failed", task.exception)
                    enableForm()
                }
            }
    }

    private fun handleSignIn() {
        val email = etEmail.text.toString().trim()
        val pass  = etPassword.text.toString().trim()
        if (!isValidEmail(email)) {
            etEmail.error = "Enter a valid email"
            return
        }
        if (pass.isEmpty()) {
            etPassword.error = "Enter your password"
            return
        }
        progress.show()
        disableForm()
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                progress.dismiss()
                if (task.isSuccessful) {
                    val user = auth.currentUser!!
                    if (user.isEmailVerified) {
                        saveUserToFirestore(user)
                        navigateToContent(user)
                    } else {
                        Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        enableForm()
                    }
                } else {
                    showError("Login failed", task.exception)
                    enableForm()
                }
            }
    }

    private fun resendVerificationEmail() {
        auth.currentUser?.takeIf { !it.isEmailVerified }?.let { user ->
            progress.show()
            user.sendEmailVerification()
                .addOnCompleteListener { task ->
                    progress.dismiss()
                    if (task.isSuccessful) {
                        Toast.makeText(this,
                            "Verification sent to ${user.email}", Toast.LENGTH_SHORT).show()
                    } else {
                        showError("Could not resend email", task.exception)
                    }
                }
        } ?: Toast.makeText(this, "No unverified user.", Toast.LENGTH_SHORT).show()
    }

    private fun handlePasswordReset() {
        val email = etEmail.text.toString().trim()
        if (!isValidEmail(email)) {
            etEmail.error = "Enter your registered email"
            return
        }
        progress.show()
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                progress.dismiss()
                if (task.isSuccessful) {
                    Toast.makeText(this,
                        "Reset link sent to $email", Toast.LENGTH_SHORT).show()
                } else {
                    showError("Reset failed", task.exception)
                }
            }
    }

    private fun saveUserToFirestore(user: FirebaseUser) {
        val data = hashMapOf(
            "uid"      to user.uid,
            "email"    to user.email,
            "joinedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(user.uid)
            .set(data)
            .addOnFailureListener { Log.w(TAG, "Firestore write failed", it) }
    }

    private fun navigateToContent(user: FirebaseUser) {
        updateUI(user)
        startActivity(Intent(this, com.adbase.gly.contentActivity::class.java))
        finish()
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            tvStatus.text = "Signed in as: ${user.email}"
            cardView.alpha = 0.5f
        } else {
            tvStatus.text = "Not signed in!"
            cardView.alpha = 1f
        }
    }

    // Helpers

    private fun isValidEmail(email: String) =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun disableForm() {
        btnSignIn.isEnabled = false
        btnSignUp.isEnabled = false
        btnVerifyEmail.isEnabled = false
        tvForgotPassword.isEnabled = false
    }

    private fun enableForm() {
        btnSignIn.isEnabled = true
        btnSignUp.isEnabled = true
        btnVerifyEmail.isEnabled = true
        tvForgotPassword.isEnabled = true
    }

    private fun showError(message: String, ex: Exception?) {
        Log.e(TAG, message, ex)
        Toast.makeText(this,
            "$message: ${ex?.localizedMessage}", Toast.LENGTH_LONG).show()
    }

    private fun sendInitialVerification() {
        val user = auth.currentUser!!
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this,
                        "Check your email: ${user.email}", Toast.LENGTH_LONG).show()
                    tvStatus.text = "Verification sent to ${user.email}"
                } else {
                    showError("Verification email failed", task.exception)
                }
                auth.signOut()
                enableForm()
            }
    }
}
