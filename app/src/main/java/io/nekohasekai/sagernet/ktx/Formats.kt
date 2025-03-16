fun parseProxies(text: String): List<AbstractBean> {
    val links = text.split('\n').flatMap { it.trim().split(' ') }
    val linksByLine = text.split('\n').map { it.trim() }

    val entities = ArrayList<AbstractBean>()
    val entitiesByLine = ArrayList<AbstractBean>()

    fun String.parseLink(entities: ArrayList<AbstractBean>) {
        if (startsWith("exclave://subscription?") || startsWith("sn://subscription?")) {
            throw SubscriptionFoundException(this)
        }

        if (startsWith("exclave://")) {
            Logs.d("Try parse universal link: $this")
            runCatching {
                entities.add(parseBackupLink(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("socks://") || startsWith("socks4://") || startsWith("socks4a://") ||
            startsWith("socks5://") || startsWith("socks5h://")) {
            Logs.d("Try parse socks link: $this")
            runCatching {
                entities.add(parseSOCKS(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (matches("(http|https)://.*".toRegex())) {
            Logs.d("Try parse http link: $this")
            runCatching {
                entities.add(parseHttp(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("vmess://") || startsWith("vless://") || startsWith("trojan://")) {
            Logs.d("Try parse v2ray link: $this")
            runCatching {
                entities.add(parseV2Ray(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("trojan-go://")) {
            Logs.d("Try parse trojan-go link: $this")
            runCatching {
                entities.add(parseTrojanGo(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ss://")) {
            Logs.d("Try parse shadowsocks link: $this")
            runCatching {
                entities.add(parseShadowsocks(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("ssr://")) {
            Logs.d("Try parse shadowsocksr link: $this")
            runCatching {
                entities.add(parseShadowsocksR(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("naive+")) {
            Logs.d("Try parse naive link: $this")
            runCatching {
                entities.add(parseNaive(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("brook://")) {
            Logs.d("Try parse brook link: $this")
            runCatching {
                entities.add(parseBrook(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("hysteria://")) {
            Logs.d("Try parse hysteria link: $this")
            runCatching {
                entities.add(parseHysteria(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("hysteria2://") || startsWith("hy2://")) {
            Logs.d("Try parse hysteria 2 link: $this")
            runCatching {
                entities.add(parseHysteria2(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("juicity://")) {
            Logs.d("Try parse juicity link: $this")
            runCatching {
                entities.add(parseJuicity(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("tuic://")) {
            Logs.d("Try parse tuic link: $this")
            runCatching {
                entities.add(parseTuic(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("wireguard://")) {
            Logs.d("Try parse wireguard link: $this")
            runCatching {
                entities.add(parseV2rayNWireGuard(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("mierus://")) {
            Logs.d("Try parse mieru link: $this")
            runCatching {
                entities.add(parseMieru(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("quic://")) {
            Logs.d("Try parse http3 link: $this")
            runCatching {
                entities.add(parseHttp3(this))
            }.onFailure {
                Logs.w(it)
            }
        } else if (startsWith("anytls://")) {
            Logs.d("Try parse anytls link: $this")
            runCatching {
                entities.add(parseAnyTLS(this))
            }.onFailure {
                Logs.w(it)
            }
        }
        } else { // Matsuri plugins
            MatsuriPluginManager.getProtocols().forEach { obj ->
                obj.protocolConfig.getJSONArray("links")?.forEach { any ->
                    if (any is String && startsWith(any)) {
                        runOnDefaultDispatcher {
                            runCatching {
                                entities.add(
                                    parseShareLink(
                                        obj.plgId, obj.protocolId, this@parseLink
                                    )
                                )
                            }.onFailure {
                                Logs.w(it)
                            }
                        }
                    }
                }
            }
        }
    }

    for (link in links) {
        link.parseLink(entities)
    }
    for (link in linksByLine) {
        link.parseLink(entitiesByLine)
    }
    var isBadLink = false
    if (entities.onEach { it.initializeDefaultValues() }.size == entitiesByLine.onEach { it.initializeDefaultValues() }.size) run test@{
        entities.forEachIndexed { index, bean ->
            val lineBean = entitiesByLine[index]
            if (bean == lineBean && bean.displayName() != lineBean.displayName()) {
                isBadLink = true
                return@test
            }
        }
    }
    runOnDefaultDispatcher {
        MatsuriJSInterface.Default.destroyAllJsi()
    }
    return if (entities.size > entitiesByLine.size) entities else entitiesByLine
}
