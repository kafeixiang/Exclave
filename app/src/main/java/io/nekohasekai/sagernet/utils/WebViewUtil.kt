/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.utils

import android.os.Build
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import io.nekohasekai.sagernet.ktx.Logs
import java.io.ByteArrayInputStream
import java.io.InputStream

object WebViewUtil {
    fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
    ) {
        if (Build.VERSION.SDK_INT >= 23 && error != null) {
            Logs.e("WebView error description: ${error.description}")
        }
        Logs.e("WebView error: ${error.toString()}")
    }

    fun interceptRequest(
        res: (String) -> InputStream?, view: WebView?, request: WebResourceRequest?
    ): WebResourceResponse {
        val path = request?.url?.path ?: "404"
        val input = res(path)
        var mime = "text/plain"
        if (path.endsWith(".js")) mime = "application/javascript"
        if (path.endsWith(".html")) mime = "text/html"
        return if (input != null) {
            WebResourceResponse(mime, "UTF-8", input)
        } else {
            WebResourceResponse(
                "text/plain", "UTF-8", ByteArrayInputStream("".toByteArray())
            )
        }
    }
}