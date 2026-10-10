// FavoritesManagementSheet.kt
package com.hundewildnis.app

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Collections

class FavoritesManagementSheet : BottomSheetDialogFragment() {

    private val favorites = mutableListOf<FavoritePOI>()
    private lateinit var adapter: ManagementAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private var notified = false

    /** Callback nach Schließen – z.B. Favoritenliste neu laden */
    var onDismissed: (() -> Unit)? = null

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        favorites.clear()
        favorites.addAll(FavoritesManager.getAll(requireContext()))
        return buildView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!notified) { notified = true; onDismissed?.invoke() }
    }

    private fun notifyAndDismiss() {
        if (!notified) { notified = true; onDismissed?.invoke() }
        dismiss()
    }

    // ════════════════════════════════════════════════════════════
    // VIEW AUFBAU
    // ════════════════════════════════════════════════════════════

    private fun buildView(): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── Header ───────────────────────────────────────────────
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding((16*dp).toInt(), (16*dp).toInt(), (12*dp).toInt(), (8*dp).toInt())
        }
        header.addView(TextView(ctx).apply {
            text      = "⭐  Favoriten verwalten"
            textSize  = 15f
            setTextColor(Color.parseColor("#8FC357"))
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(ctx).apply {
            text = "✕  Schließen"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding((8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
            setOnClickListener { notifyAndDismiss() }
        })
        root.addView(header)

        // ── Hinweiszeile ─────────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text = "☰ Halten & Ziehen zum Sortieren  ·  Antippen zum Umbenennen"
            textSize = 10f
            setTextColor(Color.parseColor("#B2B2AE"))
            setPadding((16*dp).toInt(), 0, (16*dp).toInt(), (8*dp).toInt())
        })
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.gl_divider))
        })

        // ── Leer-State ───────────────────────────────────────────
        if (favorites.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = "Noch keine Favoriten gespeichert.\nNavigationsziele über ⭐ speichern."
                textSize = 12f
                setTextColor(Color.parseColor("#B2B2AE"))
                gravity = Gravity.CENTER
                setPadding((16*dp).toInt(), (40*dp).toInt(),
                    (16*dp).toInt(), (40*dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            return root
        }

        // ── RecyclerView ─────────────────────────────────────────
        val recycler = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            layoutParams  = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (420 * dp).toInt()
            )
        }

        adapter = ManagementAdapter(
            items       = favorites,
            onStartDrag = { vh -> touchHelper.startDrag(vh) },
            onRename    = { fav -> showRenameDialog(fav) },
            onDelete    = { fav -> confirmDelete(fav) }
        )
        recycler.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.adapterPosition; val to = target.adapterPosition
                if (from < 0 || to < 0) return false
                Collections.swap(favorites, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                FavoritesManager.reorder(requireContext(), favorites)
            }
        }
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recycler)
        root.addView(recycler)
        return root
    }

    // ════════════════════════════════════════════════════════════
    // DIALOGE
    // ════════════════════════════════════════════════════════════

    private fun showRenameDialog(fav: FavoritePOI) {
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density

        val input = EditText(ctx).apply {
            setText(fav.displayName)
            selectAll()
            setPadding((16*dp).toInt(), (12*dp).toInt(),
                (16*dp).toInt(), (12*dp).toInt())
            setTextColor(Color.parseColor("#CCCCCC"))
            hint = "Neuer Name"
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle("${fav.categoryIcon}  Umbenennen")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    FavoritesManager.rename(ctx, fav.id, newName)
                    val idx = favorites.indexOfFirst { it.id == fav.id }
                    if (idx >= 0) {
                        favorites[idx] = favorites[idx].copy(customName = newName)
                        adapter.notifyItemChanged(idx)
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()
    }

    private fun confirmDelete(fav: FavoritePOI) {
        val ctx   = requireContext()

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Favorit löschen")
            .setMessage("\"${fav.displayName}\" aus Favoriten entfernen?")
            .setPositiveButton("Löschen") { _, _ ->
                FavoritesManager.delete(ctx, fav.id)
                val idx = favorites.indexOfFirst { it.id == fav.id }
                if (idx >= 0) { favorites.removeAt(idx); adapter.notifyItemRemoved(idx) }
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()
    }

    // ════════════════════════════════════════════════════════════
    // ADAPTER (innere Klasse)
    // ════════════════════════════════════════════════════════════

    inner class ManagementAdapter(
        private val items:       MutableList<FavoritePOI>,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        private val onRename:    (FavoritePOI) -> Unit,
        private val onDelete:    (FavoritePOI) -> Unit
    ) : RecyclerView.Adapter<ManagementAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val dragHandle: TextView = view.findViewWithTag("drag")
            val nameText:   TextView = view.findViewWithTag("name")
            val addrText:   TextView = view.findViewWithTag("addr")
            val deleteBtn:  TextView = view.findViewWithTag("del")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val dp  = ctx.resources.displayMetrics.density

            val wrapper = LinearLayout(ctx).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT)
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding((12*dp).toInt(), (10*dp).toInt(),
                    (12*dp).toInt(), (10*dp).toInt())
            }

            val drag = TextView(ctx).apply {
                tag = "drag"; text = "☰"; textSize = 20f
                setTextColor(Color.parseColor("#B2B2AE"))
                setPadding(0, 0, (12*dp).toInt(), 0)
            }

            val info = LinearLayout(ctx).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val name = TextView(ctx).apply {
                tag = "name"; textSize = 13f
                setTextColor(Color.parseColor("#DDDDDD"))
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val addr = TextView(ctx).apply {
                tag = "addr"; textSize = 10f
                setTextColor(Color.parseColor("#B2B2AE"))
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            info.addView(name); info.addView(addr)

            val del = TextView(ctx).apply {
                tag = "del"; text = "🗑️"; textSize = 18f
                setPadding((8*dp).toInt(), 0, 0, 0)
            }

            row.addView(drag); row.addView(info); row.addView(del)
            wrapper.addView(row)
            wrapper.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#26FFFFFF"))
            })
            return VH(wrapper)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val fav = items[position]
            holder.nameText.text = "${fav.categoryIcon}  ${fav.displayName}"
            holder.addrText.text = fav.address
                ?: "%.4f°,  %.4f°".format(fav.lat, fav.lon)

            holder.itemView.setOnClickListener { onRename(fav) }
            holder.deleteBtn.setOnClickListener { onDelete(fav) }
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
                false
            }
        }

        override fun getItemCount() = items.size
    }
}