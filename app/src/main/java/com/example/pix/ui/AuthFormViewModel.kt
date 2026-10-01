package com.example.pix.ui
import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pix.BuildConfig
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.cloud.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Operations survive recreation; AuthRepository alone decides session state. */
class AuthFormViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PixApplication
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirm by mutableStateOf("")
    var register by mutableStateOf(false)
    var recover by mutableStateOf(false)
    var busy by mutableStateOf(false); private set
    var message by mutableIntStateOf(0); private set
    var success by mutableStateOf(false); private set
    fun loginMode() { register=false; recover=false; message=0; password=""; confirm=""; app.auth.showLogin() }
    fun toggleRegistration() { register=!register; recover=false; message=0; password=""; confirm="" }
    fun toggleRecovery() { recover=!recover; register=false; message=0 }
    fun submit() {
        val invalid=when {
            !AuthValidation.validEmail(email) -> R.string.auth_invalid_email
            recover -> null
            register -> AuthValidation.registration(email,password,confirm)
            password.isEmpty() -> R.string.auth_invalid_credentials
            else -> null
        }
        if(invalid!=null) {message=invalid;success=false;return}
        val address=email.trim();val secret=password;val signup=register;val recovery=recover
        perform {
            val result=when {recovery->app.auth.recover(address);signup->app.auth.signUp(address,secret);else->app.auth.signIn(address,secret)}
            if(result.isSuccess) {
                password="";confirm=""
                if(recovery) {message=R.string.auth_recover_sent;success=true}
                if(signup) register=false
            }
            result
        }
    }
    fun resend(address:String=email) {email=address;perform {app.auth.resendConfirmation(address)}}
    fun google(activity:Activity)=perform {
        val token=GoogleSignInHelper(app.cloud).token(activity)
        app.auth.signInGoogle(token.idToken,token.nonce)
    }
    private fun perform(action:suspend ()->Result<Unit>) {
        if(busy)return
        busy=true;message=0;success=false
        viewModelScope.launch {
            try {action().exceptionOrNull()?.let {message=GoogleAuthErrors.message(it)}}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){
                message=GoogleAuthErrors.message(error)
                // No exception message, stack, credential, callback URL or token in logs.
                if(BuildConfig.DEBUG) Log.w("PixAuth","Credential failure: ${error.javaClass.simpleName}; category=$message")
            } finally {busy=false}
        }
    }
}
