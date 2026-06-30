package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.hundewildnis.app.databinding.ActivityPlantScoutBinding

// ════════════════════════════════════════════════════════════════
// GEFAHRENPFLANZEN-SCOUT – Hauptscreen
// Datei: PlantScoutActivity.kt
//
// Zeigt:
//   1. Saisonale Warnung (aktueller Monat)
//   2. Filterbare Pflanzenliste (Suche + Toxizitäts-Filter)
//   3. Tipp-to-Detail Navigation
// ════════════════════════════════════════════════════════════════

class PlantScoutActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityPlantScoutBinding
    private lateinit var adapter: PlantListAdapter

    private var currentFilter: PlantToxicityLevel? = null
    private var showOnlySeasonal = false
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlantScoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSeasonBanner()
        setupFilterSpinner()
        setupSearchField()
        setupRecyclerView()
        applyFilter()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_plant_scout, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_seasonal -> {
                showOnlySeasonal = !showOnlySeasonal
                item.isChecked = showOnlySeasonal
                item.title = if (showOnlySeasonal) "Alle zeigen" else "Nur aktuell"
                applyFilter()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "🌿 Gefahrenpflanzen-Scout"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupSeasonBanner() {
        val month = ToxicPlantDatabase.currentMonth()
        val seasonal = ToxicPlantDatabase.getBySeason()

        if (seasonal.isNotEmpty()) {
            binding.cardSeasonBanner.visibility = View.VISIBLE
            binding.tvSeasonTitle.text = "⚠️ Aktuell im ${month} besonders relevant"
            val highRisk = seasonal.filter {
                it.toxicityLevel == PlantToxicityLevel.DEADLY ||
                it.toxicityLevel == PlantToxicityLevel.HIGHLY_TOXIC
            }
            val names = highRisk.take(3).joinToString(", ") { it.name }
            binding.tvSeasonPlants.text = if (highRisk.isNotEmpty())
                "Achtung: $names${if (highRisk.size > 3) " +" else ""}"
            else
                "${seasonal.size} Pflanzen in dieser Jahreszeit relevant"

            binding.cardSeasonBanner.setOnClickListener {
                showOnlySeasonal = true
                applyFilter()
                binding.cardSeasonBanner.strokeWidth = 3
            }
        } else {
            binding.cardSeasonBanner.visibility = View.GONE
        }
    }

    private fun setupFilterSpinner() {
        val options = listOf(
            "Giftstufen",
            "☠️ Tödlich",
            "🔴 Stark giftig",
            "🟠 Giftig",
            "🟡 Leicht giftig"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = adapter

        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentFilter = when (pos) {
                    1 -> PlantToxicityLevel.DEADLY
                    2 -> PlantToxicityLevel.HIGHLY_TOXIC
                    3 -> PlantToxicityLevel.MODERATELY_TOXIC
                    4 -> PlantToxicityLevel.SLIGHTLY_TOXIC
                    else -> null
                }
                applyFilter()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupSearchField() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = PlantListAdapter(mutableListOf()) { plant ->
            val intent = Intent(this, PlantDetailActivity::class.java).apply {
                putExtra(PlantDetailActivity.EXTRA_PLANT_ID, plant.id)
            }
            startActivity(intent)
        }
        binding.recyclerPlants.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.recyclerPlants.adapter = adapter
    }

    // ════════════════════════════════════════════════════════════
    // FILTERLOGIK
    // ════════════════════════════════════════════════════════════

    private fun applyFilter() {
        var list = if (currentQuery.length >= 2)
            ToxicPlantDatabase.search(currentQuery)
        else
            ToxicPlantDatabase.plants

        if (showOnlySeasonal) {
            val month = ToxicPlantDatabase.currentMonthInt()
            list = list.filter { month in it.bloomMonths }
        }

        currentFilter?.let { filter ->
            list = list.filter { it.toxicityLevel == filter }
        }

        // Sortierung: Gefährlichste zuerst
        list = list.sortedByDescending { it.toxicityLevel.ordinal }

        adapter.updateList(list)

        binding.tvResultCount.text = "${list.size} Pflanze(n)" +
            if (showOnlySeasonal) " · Aktuelle Jahreszeit" else ""

        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
