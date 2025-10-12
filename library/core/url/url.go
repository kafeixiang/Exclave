// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// license: https://go.dev/LICENSE

// workaround https://github.com/golang/go/issues/75815
// taken from https://github.com/golang/go/blob/9db7e30bb42eed9912f5e7e9e3959f3b38879d5b/src/net/url/url.go

package url

import (
	"errors"
	"fmt"
	"net/netip"
	neturl "net/url"
	"strings"
	_ "unsafe"
)

//go:linkname setFragment net/url.(*URL).setFragment
func setFragment(u *neturl.URL, fragment string) error

//go:linkname setPath net/url.(*URL).setPath
func setPath(u *neturl.URL, fragment string) error

//go:linkname validUserinfo net/url.validUserinfo
func validUserinfo(s string) bool

//go:linkname unescape net/url.unescape
func unescape(s string, mode encoding) (string, error)

//go:linkname getScheme net/url.getScheme
func getScheme(rawURL string) (scheme, path string, err error)

//go:linkname validOptionalPort net/url.validOptionalPort
func validOptionalPort(port string) bool

//go:linkname stringContainsCTLByte net/url.stringContainsCTLByte
func stringContainsCTLByte(s string) bool

type encoding int

const (
	encodePath encoding = 1 + iota
	encodePathSegment
	encodeHost
	encodeZone
	encodeUserPassword
	encodeQueryComponent
	encodeFragment
)

func Parse(rawURL string) (*neturl.URL, error) {
	u, frag, _ := strings.Cut(rawURL, "#")
	url, err := parse(u)
	if err != nil {
		return nil, &neturl.Error{Op: "parse", URL: u, Err: err}
	}
	if frag == "" {
		return url, nil
	}
	if err = setFragment(url, frag); err != nil {
		return nil, &neturl.Error{Op: "parse", URL: rawURL, Err: err}
	}
	return url, nil
}

func parse(rawURL string) (*neturl.URL, error) {
	var rest string
	var err error

	if stringContainsCTLByte(rawURL) {
		return nil, errors.New("net/url: invalid control character in URL")
	}

	url := new(neturl.URL)

	if rawURL == "*" {
		url.Path = "*"
		return url, nil
	}

	if url.Scheme, rest, err = getScheme(rawURL); err != nil {
		return nil, err
	}
	url.Scheme = strings.ToLower(url.Scheme)

	if strings.HasSuffix(rest, "?") && strings.Count(rest, "?") == 1 {
		url.ForceQuery = true
		rest = rest[:len(rest)-1]
	} else {
		rest, url.RawQuery, _ = strings.Cut(rest, "?")
	}

	if !strings.HasPrefix(rest, "/") {
		if url.Scheme != "" {
			url.Opaque = rest
			return url, nil
		}

		if segment, _, _ := strings.Cut(rest, "/"); strings.Contains(segment, ":") {
			return nil, errors.New("first path segment in URL cannot contain colon")
		}
	}

	if !strings.HasPrefix(rest, "///") && strings.HasPrefix(rest, "//") {
		var authority string
		authority, rest = rest[2:], ""
		if i := strings.Index(authority, "/"); i >= 0 {
			authority, rest = authority[:i], authority[i:]
		}
		url.User, url.Host, err = parseAuthority(authority)
		if err != nil {
			return nil, err
		}
	} else if url.Scheme != "" && strings.HasPrefix(rest, "/") {
		url.OmitHost = true
	}

	if err := setPath(url, rest); err != nil {
		return nil, err
	}
	return url, nil
}

func parseAuthority(authority string) (user *neturl.Userinfo, host string, err error) {
	i := strings.LastIndex(authority, "@")
	if i < 0 {
		host, err = parseHost(authority)
	} else {
		host, err = parseHost(authority[i+1:])
	}
	if err != nil {
		return nil, "", err
	}
	if i < 0 {
		return nil, host, nil
	}
	userinfo := authority[:i]
	if !validUserinfo(userinfo) {
		return nil, "", errors.New("net/url: invalid userinfo")
	}
	if !strings.Contains(userinfo, ":") {
		if userinfo, err = unescape(userinfo, encodeUserPassword); err != nil {
			return nil, "", err
		}
		user = neturl.User(userinfo)
	} else {
		username, password, _ := strings.Cut(userinfo, ":")
		if username, err = unescape(username, encodeUserPassword); err != nil {
			return nil, "", err
		}
		if password, err = unescape(password, encodeUserPassword); err != nil {
			return nil, "", err
		}
		user = neturl.UserPassword(username, password)
	}
	return user, host, nil
}

func parseHost(host string) (string, error) {
	if openBracketIdx := strings.LastIndex(host, "["); openBracketIdx != -1 {
		closeBracketIdx := strings.LastIndex(host, "]")
		if closeBracketIdx < 0 {
			return "", errors.New("missing ']' in host")
		}

		colonPort := host[closeBracketIdx+1:]
		if !validOptionalPort(colonPort) {
			return "", fmt.Errorf("invalid port %q after host", colonPort)
		}
		unescapedColonPort, err := unescape(colonPort, encodeHost)
		if err != nil {
			return "", err
		}

		hostname := host[openBracketIdx+1 : closeBracketIdx]
		var unescapedHostname string
		zoneIdx := strings.Index(hostname, "%25")
		if zoneIdx >= 0 {
			hostPart, err := unescape(hostname[:zoneIdx], encodeHost)
			if err != nil {
				return "", err
			}
			zonePart, err := unescape(hostname[zoneIdx:], encodeZone)
			if err != nil {
				return "", err
			}
			unescapedHostname = hostPart + zonePart
		} else {
			var err error
			unescapedHostname, err = unescape(hostname, encodeHost)
			if err != nil {
				return "", err
			}
		}

		addr, err := netip.ParseAddr(unescapedHostname)
		if err != nil {
			return "", fmt.Errorf("invalid host: %w", err)
		}
		if addr.Is4() {
			return "", errors.New("invalid IP-literal")
		}
		return "[" + unescapedHostname + "]" + unescapedColonPort, nil
	} else if i := strings.LastIndex(host, ":"); i != -1 {
		colonPort := host[i:]
		if !validOptionalPort(colonPort) {
			return "", fmt.Errorf("invalid port %q after host", colonPort)
		}
	}

	var err error
	if host, err = unescape(host, encodeHost); err != nil {
		return "", err
	}
	return host, nil
}
