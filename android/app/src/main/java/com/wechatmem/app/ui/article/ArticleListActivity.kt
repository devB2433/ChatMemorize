package com.wechatmem.app.ui.article

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wechatmem.app.data.local.db.AppDatabase
import com.wechatmem.app.databinding.ActivityArticleListBinding
import kotlinx.coroutines.launch

class ArticleListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArticleListBinding
    private lateinit var adapter: ArticleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArticleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ArticleAdapter { article ->
            startActivity(Intent(this, ArticleDetailActivity::class.java).apply {
                putExtra("id", article.id)
            })
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadArticles()
    }

    private fun loadArticles() {
        lifecycleScope.launch {
            val articles = AppDatabase.getInstance(this@ArticleListActivity).articleDao().getAll()
            adapter.submitList(articles)
            binding.tvEmpty.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
