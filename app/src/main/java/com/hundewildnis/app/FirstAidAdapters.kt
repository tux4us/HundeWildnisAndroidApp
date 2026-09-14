package com.hundewildnis.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemEmergencyCategoryBinding
import com.hundewildnis.app.databinding.ItemEmergencySubcategoryBinding
import com.hundewildnis.app.databinding.ItemSymptomCheckBinding
import android.content.res.Configuration
import android.view.HapticFeedbackConstants

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
        holder.binding.tvCategoryName.text = category.displayName(holder.itemView.context)

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
        holder.binding.tvName.text = type.displayName(holder.itemView.context)
        holder.binding.tvUrgency.text = type.urgency.displayName(holder.itemView.context)

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
        holder.binding.tvName.text = type.displayName(holder.itemView.context)
        holder.binding.tvUrgency.text = type.urgency.displayName(holder.itemView.context)

        val urgencyColor = Color.parseColor(type.urgency.color)
        holder.binding.viewUrgencyIndicator.setBackgroundColor(urgencyColor)

        holder.binding.root.setOnClickListener {
            onTypeClick(type)
        }
    }

    override fun getItemCount() = types.size
}

// ════════════════════════════════════════════════════════════════
// SYMPTOM-CHECK ADAPTER - MIT VISUELLEM FEEDBACK
// KOMPLETTE VERSION ZUM ERSETZEN
// ════════════════════════════════════════════════════════════════

class SymptomCheckAdapter(
    private val context: android.content.Context,
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

        holder.binding.tvQuestion.text = question.question(holder.binding.root.context)

        // ✅ Dark Mode Detection
        val context = holder.binding.root.context
        val isDarkTheme = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // ✅ Farben definieren
        val selectedYesColor = Color.parseColor("#4CAF50")      // Grün
        val selectedNoColor = Color.parseColor("#F44336")        // Rot
        val unselectedBgColor = if (isDarkTheme) Color.parseColor("#2d2d2d") else Color.WHITE
        val unselectedStrokeColor = if (isDarkTheme) Color.parseColor("#555555") else Color.parseColor("#CCCCCC")

        // ✅ Reset auf Standardzustand
        resetButtonStyle(holder.binding.btnYes, unselectedBgColor, unselectedStrokeColor, isDarkTheme)
        resetButtonStyle(holder.binding.btnNo, unselectedBgColor, unselectedStrokeColor, isDarkTheme)

        // ✅ Zeige bereits gegebene Antwort
        answers[position]?.let { answer ->
            if (answer) {
                setButtonSelected(holder.binding.btnYes, selectedYesColor, Color.WHITE)
            } else {
                setButtonSelected(holder.binding.btnNo, selectedNoColor, Color.WHITE)
            }
        }

        // ✅ JA Button Listener
        holder.binding.btnYes.setOnClickListener { button ->
            // Haptisches Feedback (optional)
            button.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)

            answers[position] = true

            // Visuelles Feedback
            setButtonSelected(holder.binding.btnYes, selectedYesColor, Color.WHITE)
            resetButtonStyle(holder.binding.btnNo, unselectedBgColor, unselectedStrokeColor, isDarkTheme)

            checkIfAllAnswered()
        }

        // ✅ NEIN Button Listener
        holder.binding.btnNo.setOnClickListener { button ->
            // Haptisches Feedback (optional)
            button.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)

            answers[position] = false

            // Visuelles Feedback
            setButtonSelected(holder.binding.btnNo, selectedNoColor, Color.WHITE)
            resetButtonStyle(holder.binding.btnYes, unselectedBgColor, unselectedStrokeColor, isDarkTheme)

            checkIfAllAnswered()
        }
    }

    override fun getItemCount() = questions.size

    // ✅ NEUE HILFSFUNKTION: Button als ausgewählt markieren
    private fun setButtonSelected(
        button: com.google.android.material.button.MaterialButton,
        backgroundColor: Int,
        textColor: Int
    ) {
        button.apply {
            setBackgroundColor(backgroundColor)
            setTextColor(textColor)
            strokeWidth = 0  // Kein Rand bei ausgewählt
            elevation = 4f   // Leichte Erhebung für 3D-Effekt
        }
    }

    // ✅ NEUE HILFSFUNKTION: Button zurücksetzen
    private fun resetButtonStyle(
        button: com.google.android.material.button.MaterialButton,
        backgroundColor: Int,
        strokeColorInt: Int,
        isDarkTheme: Boolean
    ) {
        button.apply {
            setBackgroundColor(backgroundColor)
            setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
            setStrokeColorResource(android.R.color.transparent)
            strokeWidth = 2  // Dünner Rand
            elevation = 0f

            // Stroke explizit setzen
            val states = arrayOf(intArrayOf(android.R.attr.state_enabled))
            val colors = intArrayOf(strokeColorInt)
            this.strokeColor = android.content.res.ColorStateList(states, colors)
        }
    }

    private fun checkIfAllAnswered() {
        if (answers.size == questions.size) {
            // Alle Fragen beantwortet
            val results = questions.mapIndexed { index, question ->
                question.question(context) to (answers[index] ?: false)
            }.toMap()

            onAllAnswered(results)
        }
    }
}

