package com.example.navassist

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Launcher-style search: filters installed apps and offers a Google search. */
class SearchActivity : Activity() {

    private var all: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var query = ""
    private val icons = HashMap<String, Drawable>()
    private lateinit var adapter: BaseAdapter

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun webRow() = query.isNotBlank()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        all = Apps.launchables(this)
        shown = all

        val root = FrameLayout(this)
        root.setOnClickListener { finish() }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.shape(0xF0111827.toInt(), dp(22).toFloat(), Ui.BORDER, dp(1))
        card.setPadding(dp(14), dp(14), dp(14), dp(8))
        card.isClickable = true

        // search bar
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = Ui.shape(Ui.BG, dp(16).toFloat(), Ui.BORDER, dp(1))
        bar.setPadding(dp(14), 0, dp(14), 0)
        val glass = ImageView(this)
        glass.setImageResource(R.drawable.ic_search)
        glass.alpha = 0.8f
        bar.addView(glass, LinearLayout.LayoutParams(dp(22), dp(22)))

        val input = EditText(this)
        input.hint = "Search apps or the web"
        input.setHintTextColor(Ui.MUTED)
        input.typeface = Ui.font(this, 400)
        input.setTextColor(Color.WHITE)
        input.textSize = 17f
        input.background = null
        input.isSingleLine = true
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setPadding(dp(12), dp(14), dp(4), dp(14))
        bar.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        adapter = object : BaseAdapter() {
            override fun getCount(): Int = shown.size + (if (webRow()) 1 else 0)
            override fun getItem(position: Int): Any = position
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? LinearLayout) ?: makeRow()
                val icon = row.getChildAt(0) as ImageView
                val col = row.getChildAt(1) as LinearLayout
                val title = col.getChildAt(0) as TextView
                val sub = col.getChildAt(1) as TextView
                if (webRow() && position == 0) {
                    icon.setImageResource(R.drawable.ic_search)
                    icon.setPadding(dp(9), dp(9), dp(9), dp(9))
                    icon.background = Ui.shape(Ui.VIOLET, dp(12).toFloat())
                    title.text = "Search Google for “$query”"
                    sub.text = "Opens in your browser or Google app"
                    sub.visibility = View.VISIBLE
                } else {
                    val e = shown[position - (if (webRow()) 1 else 0)]
                    icon.setPadding(0, 0, 0, 0)
                    icon.background = null
                    icon.setImageDrawable(icons.getOrPut(e.pkg) { e.info.loadIcon(packageManager) })
                    title.text = e.label
                    sub.visibility = View.GONE
                }
                return row
            }
        }

        val list = ListView(this)
        list.divider = null
        list.setSelector(ColorDrawable(0x1F6366F1))
        list.isVerticalScrollBarEnabled = false
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            if (webRow() && position == 0) {
                searchWeb()
            } else {
                launch(shown[position - (if (webRow()) 1 else 0)])
            }
        }
        val listLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        listLp.topMargin = dp(8)
        card.addView(list, listLp)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                shown = if (query.isEmpty()) all else all.filter { it.label.contains(query, ignoreCase = true) }
                adapter.notifyDataSetChanged()
            }
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && query.isNotBlank()) {
                if (shown.size == 1) launch(shown[0]) else searchWeb()
            }
            true
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        lp.setMargins(dp(12), dp(48), dp(12), dp(12))
        root.addView(card, lp)
        setContentView(root)
        input.requestFocus()
    }

    private fun makeRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(9), dp(8), dp(9))
        row.addView(ImageView(this), LinearLayout.LayoutParams(dp(42), dp(42)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, 0, 0)
        col.addView(Ui.text(this, "", 15f, Ui.TEXT))
        col.addView(Ui.text(this, "", 12f, Ui.MUTED))
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun launch(e: AppEntry) {
        try {
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(e.pkg, e.activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(i)
            finish()
        } catch (ex: Exception) {
            Toast.makeText(this, "Could not open ${e.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchWeb() {
        try {
            startActivity(
                Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            } catch (e2: Exception) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
