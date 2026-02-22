package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.ktx.getColorAttr

class CollapsiblePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceCategoryStyle,
    defStyleRes: Int = 0
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {

    var expanded = false
        private set

    var onStateChanged: (() -> Unit)? = null

    override fun isSelectable(): Boolean = true

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        // 恢复状态，默认折叠
        expanded = key?.let { sharedPreferences?.getBoolean("${it}_expanded", false) } ?: false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // 强制设置点击监听，防止基类逻辑拦截
        holder.itemView.setOnClickListener {
            toggleExpanded()
        }

        // 统一图标颜色
        val iconView = holder.findViewById(android.R.id.icon) as? ImageView
        iconView?.setColorFilter(context.getColorAttr(androidx.preference.R.attr.colorAccent))
    }

    override fun onClick() {
        toggleExpanded()
    }

    private fun toggleExpanded() {
        expanded = !expanded
        key?.let { k ->
            sharedPreferences?.edit {
                putBoolean("${k}_expanded", expanded)
            }
        }
        updateChildren()
        onStateChanged?.invoke()
    }

    private fun updateChildren() {
        for (i in 0 until preferenceCount) {
            getPreference(i).isVisible = expanded
        }
    }

    override fun addPreference(preference: Preference): Boolean {
        val result = super.addPreference(preference)
        if (result) {
            preference.isVisible = expanded
        }
        return result
    }

    // 处理 PreferenceGroup 内部状态变化
    override fun onAttached() {
        super.onAttached()
        updateChildren()
        onStateChanged?.invoke()
    }
}
