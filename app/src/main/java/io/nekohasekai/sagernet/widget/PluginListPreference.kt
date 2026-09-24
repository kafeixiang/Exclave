package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import io.nekohasekai.sagernet.database.DataStore

class PluginListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    override fun getSummary(): CharSequence? {
        val count = DataStore.matsuriPlugins.split("\n").filter { it.isNotEmpty() }.size
        return if (count > 0) {
            "$count plugin(s) selected"
        } else {
            super.getSummary()
        }
    }

    fun postUpdate() {
        notifyChanged()
    }

}
