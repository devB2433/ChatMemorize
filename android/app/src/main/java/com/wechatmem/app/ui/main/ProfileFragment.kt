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
import com.wechatmem.app.data.migration.MigrationService
import com.wechatmem.app.data.remote.ApiService
import com.wechatmem.app.databinding.FragmentProfileBinding
import com.wechatmem.app.data.local.llm.LlmModelManager
import com.wechatmem.app.ui.article.ArticleListActivity
import com.wechatmem.app.ui.login.LoginActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val llmModels = arrayOf("glm-4-flash", "glm-4-air", "glm-4", "glm-4-plus")
    private var selectedLocalModelId: String? = null
    private var downloadJob: Job? = null

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

        // LLM backend RadioGroup
        val backend = AppPrefs.getLlmBackend(ctx)
        if (backend == "local") binding.rgLlmBackend.check(R.id.rbLocal)
        else binding.rgLlmBackend.check(R.id.rbCloud)
        updateLlmBackendUI(backend == "local")

        // Local model dropdown
        val localModelNames = LlmModelManager.AVAILABLE_MODELS.map { it.displayName }
        val localModelAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, localModelNames)
        binding.actvLocalModel.setAdapter(localModelAdapter)
        val savedLocalModel = AppPrefs.getLocalLlmModel(ctx)
        selectedLocalModelId = if (savedLocalModel.isNotBlank()) savedLocalModel else LlmModelManager.AVAILABLE_MODELS[0].id
        val savedModelInfo = LlmModelManager.getInfo(selectedLocalModelId ?: "")
        binding.actvLocalModel.setText(savedModelInfo?.displayName ?: "", false)
        updateLocalModelStatus()

        binding.rgLlmBackend.setOnCheckedChangeListener { _, checkedId ->
            updateLlmBackendUI(checkedId == R.id.rbLocal)
        }
        binding.actvLocalModel.setOnItemClickListener { _, _, position, _ ->
            selectedLocalModelId = LlmModelManager.AVAILABLE_MODELS[position].id
            updateLocalModelStatus()
        }
        binding.btnDownloadModel.setOnClickListener { startDownload() }
        binding.btnPauseResumeModel.setOnClickListener { togglePauseResume() }
        binding.btnDeleteModel.setOnClickListener {
            val modelId = selectedLocalModelId ?: return@setOnClickListener
            LlmModelManager.deleteModel(ctx, modelId)
            updateLocalModelStatus()
        }

        binding.switchLocalMode.setOnCheckedChangeListener { _, _ -> updateModeUI() }
        binding.btnMigrate.setOnClickListener { confirmMigrate() }
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnTestZhipuKey.setOnClickListener { testZhipuKey() }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_FORCE_SHOW, true)
            })
        }
        binding.btnLogout.setOnClickListener { logout() }
        binding.btnArticleList.setOnClickListener {
            startActivity(Intent(requireContext(), ArticleListActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccountUI()
    }

    private fun updateModeUI() {
        val isLocal = binding.switchLocalMode.isChecked
        binding.cloudSection.visibility = if (isLocal) View.GONE else View.VISIBLE
    }

    private fun updateAccountUI() {
        val ctx = requireContext()
        val loggedIn = AppPrefs.isLoggedIn(ctx)
        if (loggedIn) {
            binding.tvAccountStatus.visibility = View.VISIBLE
            binding.tvAccountStatus.text = "已登录云端账号"
            binding.btnLogin.visibility = View.GONE
            binding.btnLogout.visibility = View.VISIBLE
        } else {
            binding.tvAccountStatus.visibility = View.GONE
            binding.btnLogin.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.GONE
        }
    }

    private fun updateLlmBackendUI(isLocal: Boolean) {
        binding.sectionCloudLlm.visibility = if (isLocal) View.GONE else View.VISIBLE
        binding.sectionLocalLlm.visibility = if (isLocal) View.VISIBLE else View.GONE
    }

    private fun updateLocalModelStatus() {
        val ctx = requireContext()
        val modelId = selectedLocalModelId ?: return
        val info = LlmModelManager.getInfo(modelId) ?: return
        val downloaded = LlmModelManager.isDownloaded(ctx, modelId)
        val resumeBytes = LlmModelManager.resumeBytes(ctx, modelId)
        val hasPartial = resumeBytes > 0 && !downloaded

        binding.tvLocalModelStatus.text = when {
            downloaded -> "已下载 (${info.sizeDesc}，需 ${info.ramRequired} RAM)"
            hasPartial -> "已下载 ${resumeBytes / 1_048_576}MB，可继续 (${info.sizeDesc})"
            else -> "未下载 (${info.sizeDesc}，需 ${info.ramRequired} RAM)"
        }
        binding.btnDownloadModel.visibility = if (downloaded) View.GONE else View.VISIBLE
        binding.btnDownloadModel.text = if (hasPartial) "继续下载" else "下载"
        binding.btnPauseResumeModel.visibility = View.GONE
        binding.btnDeleteModel.visibility = if (downloaded || hasPartial) View.VISIBLE else View.GONE
    }

    private fun startDownload() {
        val ctx = requireContext()
        val modelId = selectedLocalModelId ?: return
        binding.btnDownloadModel.visibility = View.GONE
        binding.btnPauseResumeModel.visibility = View.VISIBLE
        binding.btnPauseResumeModel.text = "暂停"
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        downloadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                LlmModelManager.download(ctx, modelId) { progress ->
                    activity?.runOnUiThread {
                        binding.progressDownload.progress = progress
                        binding.tvDownloadPercent.text = "$progress%"
                    }
                }
                binding.layoutDownloadProgress.visibility = View.GONE
                binding.btnPauseResumeModel.visibility = View.GONE
                updateLocalModelStatus()
                Toast.makeText(ctx, "模型下载完成", Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Paused — keep tmp, update UI
                activity?.runOnUiThread {
                    binding.layoutDownloadProgress.visibility = View.GONE
                    binding.btnPauseResumeModel.visibility = View.GONE
                    updateLocalModelStatus()
                }
            } catch (e: Exception) {
                binding.layoutDownloadProgress.visibility = View.GONE
                binding.btnPauseResumeModel.visibility = View.GONE
                updateLocalModelStatus()
                Toast.makeText(ctx, "下载失败: ${e.message?.take(40)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePauseResume() {
        val job = downloadJob
        if (job != null && job.isActive) {
            // Pause
            job.cancel()
            downloadJob = null
        } else {
            // Resume
            startDownload()
        }
    }

    private fun downloadModel() = startDownload()

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
        AppPrefs.setLlmBackend(ctx, if (binding.rgLlmBackend.checkedRadioButtonId == R.id.rbLocal) "local" else "cloud")
        AppPrefs.setLocalLlmModel(ctx, selectedLocalModelId ?: "")

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
