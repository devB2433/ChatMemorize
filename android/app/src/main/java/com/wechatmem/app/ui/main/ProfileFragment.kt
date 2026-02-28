package com.wechatmem.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wechatmem.app.R
import com.wechatmem.app.data.local.AppPrefs
import com.wechatmem.app.data.local.embedding.ModelDownloader
import com.wechatmem.app.data.migration.MigrationService
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.databinding.FragmentProfileBinding
import com.wechatmem.app.ui.login.LoginActivity
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val llmModels = arrayOf("glm-4-flash", "glm-4-air", "glm-4", "glm-4-plus")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        // Load current values
        binding.switchLocalMode.isChecked = AppPrefs.isLocalMode(ctx)
        binding.etZhipuKey.setText(AppPrefs.getZhipuApiKey(ctx))
        binding.etServerUrl.setText(AppPrefs.getServerUrl(ctx))
        binding.etApiToken.setText(AppPrefs.getApiToken(ctx))

        // LLM model dropdown
        val modelAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, llmModels)
        binding.actvLlmModel.setAdapter(modelAdapter)
        binding.actvLlmModel.setText(AppPrefs.getLlmModel(ctx), false)

        updateModeUI()
        updateModelStatus()

        binding.switchLocalMode.setOnCheckedChangeListener { _, _ -> updateModeUI() }
        binding.btnMigrate.setOnClickListener { confirmMigrate() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnTestZhipuKey.setOnClickListener { testZhipuKey() }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnLogout.setOnClickListener { logout() }
    }

    private fun updateModeUI() {
        val isLocal = binding.switchLocalMode.isChecked
        binding.cloudSection.visibility = if (isLocal) View.GONE else View.VISIBLE
    }

    private fun updateModelStatus() {
        binding.tvModelStatus.text = if (ModelDownloader.isReady(requireContext()))
            "Embedding 模型已就绪" else "Embedding 模型将在首次搜索时自动初始化"
    }

    private fun confirmMigrate() {
        val ctx = requireContext()
        if (!AppPrefs.isLoggedIn(ctx)) {
            Toast.makeText(ctx, "请先登录云端账号", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(ctx)
            .setTitle("迁移数据")
            .setMessage("将本地所有对话上传到云端服务器，服务端会重新进行 embedding。确认继续？")
            .setPositiveButton("确认") { _, _ -> migrate() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun migrate() {
        binding.btnMigrate.isEnabled = false
        binding.tvMigrateStatus.visibility = View.VISIBLE
        binding.tvMigrateStatus.text = "迁移中..."
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                MigrationService(requireContext()).migrateToCloud { p ->
                    activity?.runOnUiThread {
                        binding.tvMigrateStatus.text = "迁移中 ${p.current}/${p.total}: ${p.title ?: ""}"
                    }
                }
                binding.tvMigrateStatus.text = "迁移完成"
            } catch (e: Exception) {
                binding.tvMigrateStatus.text = "迁移失败: ${e.message}"
            } finally {
                binding.btnMigrate.isEnabled = true
            }
        }
    }

    private fun saveSettings() {
        val ctx = requireContext()
        val isLocal = binding.switchLocalMode.isChecked
        AppPrefs.setLocalMode(ctx, isLocal)
        AppPrefs.setZhipuApiKey(ctx, binding.etZhipuKey.text?.toString()?.trim() ?: "")
        AppPrefs.setLlmModel(ctx, binding.actvLlmModel.text?.toString()?.trim() ?: "glm-4-flash")

        if (!isLocal) {
            val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
            if (url.isBlank()) {
                Toast.makeText(ctx, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                return
            }
            AppPrefs.setServerUrl(ctx, url)
            AppPrefs.setApiToken(ctx, binding.etApiToken.text?.toString()?.trim() ?: "")
            ApiService.invalidate()
        }
        Toast.makeText(ctx, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun testZhipuKey() {
        val key = binding.etZhipuKey.text?.toString()?.trim() ?: ""
        if (key.isBlank()) {
            binding.tvZhipuTestResult.visibility = View.VISIBLE
            binding.tvZhipuTestResult.text = "请先输入 API Key"
            binding.tvZhipuTestResult.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.error))
            return
        }
        binding.btnTestZhipuKey.isEnabled = false
        binding.tvZhipuTestResult.visibility = View.VISIBLE
        binding.tvZhipuTestResult.text = "测试中…"
        binding.tvZhipuTestResult.setTextColor(
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val model = binding.actvLlmModel.text?.toString()?.trim()?.ifBlank { "glm-4-flash" } ?: "glm-4-flash"
                com.wechatmem.app.data.remote.LlmClient(key, model).ask("你好", "")
                binding.tvZhipuTestResult.text = "✓ 连接成功"
                binding.tvZhipuTestResult.setTextColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.dot_green))
            } catch (e: Exception) {
                binding.tvZhipuTestResult.text = "✗ ${e.message?.take(40)}"
                binding.tvZhipuTestResult.setTextColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.error))
            } finally {
                binding.btnTestZhipuKey.isEnabled = true
            }
        }
    }

    private fun testConnection() {
        val ctx = requireContext()
        val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
        if (url.isBlank()) {
            Toast.makeText(ctx, "请先输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        AppPrefs.setServerUrl(ctx, url)
        AppPrefs.setApiToken(ctx, binding.etApiToken.text?.toString()?.trim() ?: "")
        ApiService.invalidate()

        binding.tvTestResult.visibility = View.VISIBLE
        binding.tvTestResult.text = "连接中..."
        binding.tvTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        binding.btnTestConnection.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiService.getInstance(ctx).getConversations(1, 1)
                binding.tvTestResult.text = "连接成功"
                binding.tvTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            } catch (e: Exception) {
                binding.tvTestResult.text = "连接失败: ${e.message}"
                binding.tvTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.error))
            } finally {
                binding.btnTestConnection.isEnabled = true
            }
        }
    }

    private fun logout() {
        val ctx = requireContext()
        AppPrefs.logout(ctx)
        ApiService.invalidate()
        startActivity(Intent(ctx, LoginActivity::class.java))
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
