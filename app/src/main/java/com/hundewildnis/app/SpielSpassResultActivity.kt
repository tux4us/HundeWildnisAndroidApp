package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivitySpielSpassResultBinding
import com.hundewildnis.app.databinding.ItemSpielAktivitaetBinding

/**
 * Zeigt die nach Örtlichkeit und Parametern gefilterte Liste an Vorschlägen.
 */
class SpielSpassResultActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySpielSpassResultBinding
    private lateinit var profile: DogProfile
    private var ortName: String = SpielOrt.GARTEN.name
    private val aktivitaeten = mutableListOf<SpielAktivitaet>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpielSpassResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")!!
        ortName = intent.getStringExtra("ort") ?: SpielOrt.GARTEN.name
        val ids = intent.getStringArrayListExtra("aktivitaetIds") ?: arrayListOf()
        aktivitaeten.addAll(ids.mapNotNull { SpielAktivitaetCatalog.findById(it) })

        setupUI()
        setupRecyclerView()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.spiel_spass_result_toolbar_title)

        if (aktivitaeten.isEmpty()) {
            binding.rvResults.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvResultCount.visibility = View.GONE
        } else {
            binding.rvResults.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            binding.tvResultCount.visibility = View.VISIBLE
            binding.tvResultCount.text = getString(R.string.spiel_spass_result_count, aktivitaeten.size)
        }

        binding.btnChangeFilter.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = SpielAktivitaetAdapter(aktivitaeten) { aktivitaet ->
            val intent = Intent(this, SpielSpassDetailActivity::class.java).apply {
                putExtra("profile", profile)
                putExtra("aktivitaetId", aktivitaet.id)
                putExtra("ort", ortName)
            }
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// ════════════════════════════════════════════════════════════════
// ADAPTER FÜR ERGEBNISLISTE
// ════════════════════════════════════════════════════════════════

class SpielAktivitaetAdapter(
    private val items: List<SpielAktivitaet>,
    private val onClick: (SpielAktivitaet) -> Unit
) : RecyclerView.Adapter<SpielAktivitaetAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemSpielAktivitaetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSpielAktivitaetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val aktivitaet = items[position]
        val context = holder.itemView.context

        holder.binding.tvName.text = aktivitaet.name(context)
        holder.binding.tvDauer.text = aktivitaet.dauerText(context)
        holder.binding.tvFokus.text = aktivitaet.fokus.joinToString(" · ") { it.icon }
        holder.binding.tvMaterial.text = aktivitaet.material.displayName(context)

        holder.binding.root.setOnClickListener { onClick(aktivitaet) }
    }

    override fun getItemCount() = items.size
}
