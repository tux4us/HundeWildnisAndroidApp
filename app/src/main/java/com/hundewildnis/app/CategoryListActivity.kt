package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivityCategoryListBinding
import com.hundewildnis.app.databinding.ItemTrainingCategoryBinding

class CategoryListActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityCategoryListBinding
    private lateinit var profile: DogProfile
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")!!

        setupUI()
        setupRecyclerView()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Trainingskategorien"
        
        binding.tvDogName.text = profile.name
        binding.tvDogInfo.text = "Wähle eine Kategorie für das Training"
    }

    private fun setupRecyclerView() {
        // Alle 18 Kategorien
        val categories = TrainingCategory.values().toList()
        
        adapter = CategoryAdapter(categories) { category ->
            openTrainingDetail(category)
        }
        
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = adapter
    }

    private fun openTrainingDetail(category: TrainingCategory) {
        val intent = Intent(this, TrainingDetailActivity::class.java)
        intent.putExtra("profile", profile)
        intent.putExtra("category_id", category.id)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// ════════════════════════════════════════════════════════════════
// ADAPTER FÜR KATEGORIEN-LISTE
// ════════════════════════════════════════════════════════════════

class CategoryAdapter(
    private val categories: List<TrainingCategory>,
    private val onCategoryClick: (TrainingCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemTrainingCategoryBinding) : 
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrainingCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        
        holder.binding.tvCategoryName.text = category.displayName
        holder.binding.tvCategoryDescription.text = category.description
        
        // Zeige Fortschritt wenn vorhanden
        // (wird in TrainingDetailActivity genauer angezeigt)
        holder.binding.root.setOnClickListener {
            onCategoryClick(category)
        }
    }

    override fun getItemCount() = categories.size
}
