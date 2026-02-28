package com.wechatmem.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.model.AuthRequest
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.databinding.ActivityLoginBinding
import com.wechatmem.app.ui.main.MainActivity
import com.wechatmem.app.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip login if local mode or already authenticated
        if (AppPrefs.isLocalMode(this) || AppPrefs.isLoggedIn(this)) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSubmit.setOnClickListener { submit() }
        binding.tvToggle.setOnClickListener { toggleMode() }
        binding.tvServerConfig.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode
        binding.tvTitle.text = getString(if (isLoginMode) R.string.title_login else R.string.title_register)
        binding.btnSubmit.text = getString(if (isLoginMode) R.string.title_login else R.string.title_register)
        binding.tvToggle.text = getString(if (isLoginMode) R.string.label_go_register else R.string.label_go_login)
        binding.tvError.visibility = View.GONE
    }

    private fun submit() {
        val username = binding.etUsername.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        if (username.length < 2) {
            showError("用户名至少 2 个字符")
            return
        }
        if (password.length < 6) {
            showError("密码至少 6 个字符")
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = ApiService.getInstance(this@LoginActivity)
                val body = AuthRequest(username, password)
                val result = if (isLoginMode) api.login(body) else api.register(body)

                AppPrefs.setApiToken(this@LoginActivity, result.token)
                ApiService.invalidate() // Rebuild with new token
                goToMain()
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("409") == true -> "用户名已存在"
                    e.message?.contains("401") == true -> "用户名或密码错误"
                    else -> "操作失败: ${e.message}"
                }
                showError(msg)
            } finally {
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
