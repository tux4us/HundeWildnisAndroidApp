package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityNasenarbeitHundBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NasenarbeitHundActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityNasenarbeitHundBinding
    private lateinit var profile: DogProfile
    private val sessions = mutableListOf<NasenarbeitSession>()
    private lateinit var adapter: NasenarbeitSessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasenarbeitHundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        profile = intent.getParcelableExtra("profile")!!

        setupUI()
        setupRecyclerView()
        loadData()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.nasenarbeit_hund_toolbar_title, profile.name)

        // Foto
        if (profile.photoUri.isNotEmpty()) {
            binding.ivDogPhoto.setImageURI(Uri.parse(profile.photoUri))
        } else {
            binding.ivDogPhoto.setImageResource(R.drawable.ic_dog_placeholder)
        }
        binding.tvDogName.text = profile.name
        binding.tvDogBreed.text = "${profile.breed}, ${profile.gender}"

        setupGrids()
    }

    private fun setupGrids() {
        // Neues Training
        val colorsNew = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridNasenarbeitNew.layoutManager = GridLayoutManager(this, 1)
        binding.gridNasenarbeitNew.adapter = StartTileAdapter(
            tiles = listOf(StartTile("nasenarbeit_new", R.drawable.ic_nose_work, getString(R.string.nasenarbeit_hund_btn_new))),
            chipColorRes = colorsNew.chipColorRes,
            accentColorRes = colorsNew.accentColorRes,
            onTileClick = {
                val intent = Intent(this, NasenarbeitBeratungActivity::class.java)
                intent.putExtra("profile", profile)
                startActivity(intent)
            }
        )

        // Export
        val colorsExport = StartGroupColors.forGroup(this, 1) // Ocker/Gelb
        binding.gridNasenarbeitExport.layoutManager = GridLayoutManager(this, 2)
        binding.gridNasenarbeitExport.adapter = StartTileAdapter(
            tiles = listOf(
                StartTile("export_pdf", R.drawable.ic_notes, "📄 PDF"),
                StartTile("export_csv", R.drawable.ic_notes, "📊 CSV")
            ),
            chipColorRes = colorsExport.chipColorRes,
            accentColorRes = colorsExport.accentColorRes,
            onTileClick = { id ->
                when(id) {
                    "export_pdf" -> exportGesamtPDF()
                    "export_csv" -> exportCSV()
                }
            }
        )
    }

    private fun setupRecyclerView() {
        adapter = NasenarbeitSessionAdapter(
            sessions = sessions,
            onViewClick = { session -> openSessionDetail(session) },
            onDeleteClick = { session -> confirmDeleteSession(session) },
            onShareClick = { session -> exportSessionPDF(session) }
        )
        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = adapter
    }

    private fun loadData() {
        val fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)
        val allSessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)

        // Stats
        binding.tvStatV.text = getString(R.string.nasenarbeit_hund_stat_line, fortschritt.sessionsVerlorensuche, getString(R.string.nasenarbeit_disziplin_verlorensuche), fortschritt.levelVerlorensuche.displayName(this))
        binding.tvStatP.text = getString(R.string.nasenarbeit_hund_stat_line, fortschritt.sessionsPersonensuche, getString(R.string.nasenarbeit_disziplin_personensuche), fortschritt.levelPersonensuche.displayName(this))
        binding.tvStatF.text = getString(R.string.nasenarbeit_hund_stat_line, fortschritt.sessionsFreisuche, getString(R.string.nasenarbeit_disziplin_freisuche), fortschritt.levelFreisuche.displayName(this))
        binding.tvStatGesamt.text = getString(R.string.nasenarbeit_hund_stat_total, fortschritt.gesamtSessions)

        if (fortschritt.maxSchritte > 0)
            binding.tvMaxLeistung.text = getString(R.string.nasenarbeit_hund_max_leistung, fortschritt.maxSchritte, String.format("%.1f", fortschritt.maxHa))

        sessions.clear()
        sessions.addAll(allSessions)
        adapter.notifyDataSetChanged()

        binding.tvNoSessions.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSessions.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openSessionDetail(session: NasenarbeitSession) {
        val intent = Intent(this, NasenarbeitSessionDetailActivity::class.java)
        intent.putExtra("session", session)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }

    private fun confirmDeleteSession(session: NasenarbeitSession) {
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nasenarbeit_hund_delete_session_title))
            .setMessage(getString(R.string.nasenarbeit_hund_delete_session_message, df.format(Date(session.datum))))
            .setPositiveButton(getString(R.string.start_delete)) { _, _ ->
                NasenarbeitManager.deleteSession(this, session.id)
                loadData()
                Toast.makeText(this, getString(R.string.nasenarbeit_hund_delete_session_success), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun exportSessionPDF(session: NasenarbeitSession) {
        try {
            val file = NasenarbeitPDFGenerator.generateSessionPDF(this, session, profile, saveToPublic = true)
            if (file != null) {
                Toast.makeText(this, getString(R.string.nasenarbeit_export_pdf_success), Toast.LENGTH_LONG).show()
                sharePDF(file)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.nasenarbeit_session_detail_pdf_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportGesamtPDF() {
        try {
            val fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)
            val file = NasenarbeitPDFGenerator.generateGesamtPDF(this, sessions, fortschritt, profile, saveToPublic = true)
            if (file != null) {
                Toast.makeText(this, getString(R.string.nasenarbeit_export_pdf_success), Toast.LENGTH_LONG).show()
                sharePDF(file)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.nasenarbeit_session_detail_pdf_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCSV() {
        try {
            val file = NasenarbeitCSVGenerator.exportSessions(this, profile, sessions)
            if (file != null) {
                Toast.makeText(this, getString(R.string.nasenarbeit_export_csv_success), Toast.LENGTH_LONG).show()
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.nasenarbeit_hund_csv_share_title)))
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.nasenarbeit_generic_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePDF(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.nasenarbeit_hund_pdf_share_title)))
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onResume() { super.onResume(); loadData() }
}

// ════════════════════════════════════════════════════════════════
// ADAPTER
// ════════════════════════════════════════════════════════════════

class NasenarbeitSessionAdapter(
    private val sessions: List<NasenarbeitSession>,
    private val onViewClick: (NasenarbeitSession) -> Unit,
    private val onDeleteClick: (NasenarbeitSession) -> Unit,
    private val onShareClick: (NasenarbeitSession) -> Unit
) : RecyclerView.Adapter<NasenarbeitSessionAdapter.VH>() {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvSessionDate)
        val tvDiz: TextView = view.findViewById(R.id.tvSessionDisziplin)
        val tvInfo: TextView = view.findViewById(R.id.tvSessionInfo)
        val tvScore: TextView = view.findViewById(R.id.tvSessionScore)
        val btnDetail: ImageButton = view.findViewById(R.id.btnSessionDetail)
        val btnShare: ImageButton = view.findViewById(R.id.btnSessionShare)
        val btnDelete: ImageButton = view.findViewById(R.id.btnSessionDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nasenarbeit_session, parent, false))

    override fun getItemCount() = sessions.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = sessions[position]
        holder.tvDate.text = df.format(Date(s.datum))
        holder.tvDiz.text = "${s.durchgefuehrteDisziplin.icon} ${s.durchgefuehrteDisziplin.displayName(holder.itemView.context)}"
        holder.tvScore.text = "⭐".repeat(s.erfolgsbewertung)
        holder.tvInfo.text = buildString {
            s.durchgefuehrteSchritte?.let { append("$it " + holder.itemView.context.getString(R.string.nasenarbeit_map_result_steps_unit) + "  ") }
            s.durchgefuehrteHa?.let { append("${String.format(Locale.US, "%.1f",it)} ha  ") }
            s.suchdauerMinuten?.let { append("${it} " + holder.itemView.context.getString(R.string.nasenarbeit_session_detail_duration_unit)) }
        }
        holder.itemView.setOnClickListener { onViewClick(s) }
        holder.btnDetail.setOnClickListener { onViewClick(s) }
        holder.btnShare.setOnClickListener { onShareClick(s) }
        holder.btnDelete.setOnClickListener { onDeleteClick(s) }
    }
}