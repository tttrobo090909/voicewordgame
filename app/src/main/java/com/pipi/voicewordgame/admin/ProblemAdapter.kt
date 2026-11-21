package com.pipi.voicewordgame.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pipi.voicewordgame.R
import com.pipi.voicewordgame.game.Hints
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.util.ImageStore

/** 문제 관리 목록 한 줄: 그림 · 한글/영문 정답 · 힌트 · 유사어 · 수정/삭제. */
class ProblemAdapter(
    private val categoryLabel: (Int) -> String,
    private val onEdit: (Word) -> Unit,
    private val onDelete: (Word) -> Unit
) : RecyclerView.Adapter<ProblemAdapter.Holder>() {

    private val items = ArrayList<Word>()

    fun submit(list: List<Word>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.problemImage)
        val ko: TextView = view.findViewById(R.id.problemKo)
        val en: TextView = view.findViewById(R.id.problemEn)
        val meta: TextView = view.findViewById(R.id.problemMeta)
        val edit: Button = view.findViewById(R.id.editProblem)
        val delete: Button = view.findViewById(R.id.deleteProblem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_problem, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val word = items[position]
        val context = holder.itemView.context

        val bitmap = ImageStore.load(context, word.image, 2)
        if (bitmap != null) holder.image.setImageBitmap(bitmap) else holder.image.setImageResource(R.mipmap.ic_launcher)

        holder.ko.text = word.ko.ifBlank { "—" }
        holder.en.text = word.en
        val hint = if (Hints.isCustom(word)) "힌트: ${word.hint}" else "힌트: 첫 글자 자동"
        val synonyms = (word.synKo + word.synEn).distinct().joinToString(", ")
        val category = categoryLabel(word.categoryId)
        holder.meta.text = buildString {
            append(category)
            append(" · ")
            append(hint)
            if (synonyms.isNotBlank()) {
                append(" · 유사어: ")
                append(synonyms)
            }
        }

        holder.edit.setOnClickListener { onEdit(word) }
        holder.delete.setOnClickListener { onDelete(word) }
    }

    override fun getItemCount(): Int = items.size
}
