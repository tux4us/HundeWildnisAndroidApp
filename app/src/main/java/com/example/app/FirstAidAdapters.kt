package com.hundewildnis.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemEmergencyCategoryBinding
import com.hundewildnis.app.databinding.ItemEmergencySubcategoryBinding
import com.hundewildnis.app.databinding.ItemSymptomCheckBinding

// ════════════════════════════════════════════════════════════════
// ADAPTER FÜR ERSTE-HILFE WIZARD
// Neu erstellen: FirstAidAdapters.kt
// ════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════
// HAUPTKATEGORIEN ADAPTER (Unfall/Erkrankung)
// ═══════════════════════════════════════════════════════════
class MainCategoryAdapter(
    private val categories: List<EmergencyMainCategory>,
    private val onCategoryClick: (EmergencyMainCategory) -> Unit
) : RecyclerView.Adapter<MainCategoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemEmergencyCategoryBinding) : 
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmergencyCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        
        holder.binding.tvCategoryIcon.text = category.icon
        holder.binding.tvCategoryName.text = category.displayName
        
        holder.binding.root.setOnClickListener {
            onCategoryClick(category)
        }
    }

    override fun getItemCount() = categories.size
}

// ═══════════════════════════════════════════════════════════
// UNFALL-TYP ADAPTER
// ═══════════════════════════════════════════════════════════
class AccidentTypeAdapter(
    private val types: List<AccidentType>,
    private val onTypeClick: (AccidentType) -> Unit
) : RecyclerView.Adapter<AccidentTypeAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemEmergencySubcategoryBinding) : 
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmergencySubcategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val type = types[position]
        
        holder.binding.tvIcon.text = type.icon
        holder.binding.tvName.text = type.displayName
        holder.binding.tvUrgency.text = type.urgency.displayName
        
        val urgencyColor = Color.parseColor(type.urgency.color)
        holder.binding.viewUrgencyIndicator.setBackgroundColor(urgencyColor)
        
        holder.binding.root.setOnClickListener {
            onTypeClick(type)
        }
    }

    override fun getItemCount() = types.size
}

// ═══════════════════════════════════════════════════════════
// ERKRANKUNGS-TYP ADAPTER
// ═══════════════════════════════════════════════════════════
class IllnessTypeAdapter(
    private val types: List<IllnessType>,
    private val onTypeClick: (IllnessType) -> Unit
) : RecyclerView.Adapter<IllnessTypeAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemEmergencySubcategoryBinding) : 
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmergencySubcategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val type = types[position]
        
        holder.binding.tvIcon.text = type.icon
        holder.binding.tvName.text = type.displayName
        holder.binding.tvUrgency.text = type.urgency.displayName
        
        val urgencyColor = Color.parseColor(type.urgency.color)
        holder.binding.viewUrgencyIndicator.setBackgroundColor(urgencyColor)
        
        holder.binding.root.setOnClickListener {
            onTypeClick(type)
        }
    }

    override fun getItemCount() = types.size
}

// ═══════════════════════════════════════════════════════════
// SYMPTOM-CHECK ADAPTER
// ═══════════════════════════════════════════════════════════
class SymptomCheckAdapter(
    private val questions: List<SymptomCheckQuestion>,
    private val onAllAnswered: (Map<String, Boolean>) -> Unit
) : RecyclerView.Adapter<SymptomCheckAdapter.ViewHolder>() {

    private val answers = mutableMapOf<Int, Boolean>()

    inner class ViewHolder(val binding: ItemSymptomCheckBinding) : 
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSymptomCheckBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val question = questions[position]
        
        holder.binding.tvQuestion.text = question.question
        
        // Reset
        holder.binding.btnYes.isSelected = false
        holder.binding.btnNo.isSelected = false
        
        // Zeige bereits gegebene Antwort
        answers[position]?.let { answer ->
            if (answer) {
                holder.binding.btnYes.isSelected = true
            } else {
                holder.binding.btnNo.isSelected = true
            }
        }
        
        holder.binding.btnYes.setOnClickListener {
            answers[position] = true
            holder.binding.btnYes.isSelected = true
            holder.binding.btnNo.isSelected = false
            checkIfAllAnswered()
        }
        
        holder.binding.btnNo.setOnClickListener {
            answers[position] = false
            holder.binding.btnNo.isSelected = true
            holder.binding.btnYes.isSelected = false
            checkIfAllAnswered()
        }
    }

    override fun getItemCount() = questions.size

    private fun checkIfAllAnswered() {
        if (answers.size == questions.size) {
            // Alle Fragen beantwortet
            val results = questions.mapIndexed { index, question ->
                question.question to (answers[index] ?: false)
            }.toMap()
            
            onAllAnswered(results)
        }
    }
}
