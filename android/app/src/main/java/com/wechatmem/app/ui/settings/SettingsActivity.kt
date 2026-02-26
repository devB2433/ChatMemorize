package com.wechatmem.app.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(
            com.google.android.material.R.drawable.ic_arrow_back_black_24
        )
        binding.toolbar.navigationIcon?.setTint(
            ContextCompat.getColor(this, R.color.on_primary)
        )
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Load current values
        binding.etServerUrl.setText(AppPrefs.getServerUrl(this))
        binding.etApiToken.setText(AppPrefs.getApiToken(this))

        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun saveSettings() {
        val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
        val token = binding.etApiToken.text?.toString()?.trim() ?: ""

        if (url.isBlank()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        AppPrefs.setServerUrl(this, url)
        AppPrefs.setApiToken(this, token)
        ApiService.invalidate() // Force rebuild with new URL/token

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
        if (url.isBlank()) {
            Toast.makeText(this, "请先输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        // Temporarily save to test
        AppPrefs.setServerUrl(this, url)
        AppPrefs.setApiToken(this, binding.etApiToken.text?.toString()?.trim() ?: "")
        ApiService.invalidate()

        binding.tvTestResult.visibility = View.VISIBLE
        binding.tvTestResult.text = "连接中..."
        binding.tvTestResult.setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = ApiService.getInstance(this@SettingsActivity)
                api.getConversations(page = 1, pageSize = 1)
                binding.tvTestResult.text = "连接成功"
                binding.tvTestResult.setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity, R.color.primary
                    )
                )
            } catch (e: Exception) {
                binding.tvTestResult.text = "连接失败: ${e.message}"
                binding.tvTestResult.setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity, R.color.error
                    )
                )
            } finally {
                binding.btnTestConnection.isEnabled = true
            }
        }
    }
}
