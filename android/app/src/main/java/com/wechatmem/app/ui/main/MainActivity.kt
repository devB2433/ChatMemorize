package com.wechatmem.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.wechatmem.app.R
import com.wechatmem.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fragments = mutableMapOf<Int, Fragment>()
    private var activeId = R.id.nav_conversations

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            val conv = ConversationsFragment()
            fragments[R.id.nav_conversations] = conv
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, conv, "conv")
                .commit()
        } else {
            // Restore fragments after config change
            supportFragmentManager.fragments.forEach { f ->
                when (f) {
                    is ConversationsFragment -> fragments[R.id.nav_conversations] = f
                    is SearchFragment -> fragments[R.id.nav_search] = f
                    is ProfileFragment -> fragments[R.id.nav_profile] = f
                }
            }
            activeId = savedInstanceState.getInt("activeId", R.id.nav_conversations)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showFragment(item.itemId)
            true
        }

        if (savedInstanceState != null) {
            binding.bottomNav.selectedItemId = activeId
        }
    }

    private fun showFragment(id: Int) {
        val ft = supportFragmentManager.beginTransaction()
        // Hide current
        fragments[activeId]?.let { ft.hide(it) }
        // Show or create target
        val target = fragments[id] ?: createFragment(id).also {
            fragments[id] = it
            ft.add(R.id.fragmentContainer, it, tagFor(id))
        }
        ft.show(target)
        ft.commit()
        activeId = id
    }

    private fun createFragment(id: Int): Fragment = when (id) {
        R.id.nav_search -> SearchFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> ConversationsFragment()
    }

    private fun tagFor(id: Int) = when (id) {
        R.id.nav_conversations -> "conv"
        R.id.nav_search -> "search"
        R.id.nav_profile -> "profile"
        else -> "conv"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("activeId", activeId)
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (activeId != R.id.nav_conversations) {
            binding.bottomNav.selectedItemId = R.id.nav_conversations
        } else {
            super.onBackPressed()
        }
    }
}
