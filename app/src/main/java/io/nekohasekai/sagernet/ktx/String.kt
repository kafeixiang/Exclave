package io.nekohasekai.sagernet.ktx

import android.util.Patterns

fun String.isIP(): Boolean {
    return Patterns.IP_ADDRESS.matcher(this).matches()
}
