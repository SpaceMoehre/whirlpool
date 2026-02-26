import json
import shlex
import time
from typing import Any, Dict, Iterable, List, Optional

import yt_dlp
from yt_dlp.version import __version__ as YTDLP_VERSION

_DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14; Whirlpool) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/125.0.0.0 Mobile Safari/537.36"
)


class _CollectorLogger:
    def __init__(self) -> None:
        self.messages: List[str] = []

    def debug(self, message: str) -> None:
        self._add("debug", message)

    def info(self, message: str) -> None:
        self._add("info", message)

    def warning(self, message: str) -> None:
        self._add("warning", message)

    def error(self, message: str) -> None:
        self._add("error", message)

    def _add(self, level: str, message: str) -> None:
        text = str(message).strip()
        if not text:
            return
        self.messages.append(f"{level}: {text}")


def version() -> str:
    return YTDLP_VERSION


def _is_http_url(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    lower = value.lower()
    return lower.startswith("http://") or lower.startswith("https://")


def _to_headers(raw_headers: Any) -> Dict[str, str]:
    if not isinstance(raw_headers, dict):
        return {}

    headers: Dict[str, str] = {}
    for key, value in raw_headers.items():
        if key is None or value is None:
            continue
        key_text = str(key).strip()
        value_text = str(value).strip()
        if not key_text or not value_text:
            continue
        headers[key_text] = value_text
    return headers


def _iter_candidates(info: Dict[str, Any]) -> Iterable[Dict[str, Any]]:
    root_url = info.get("url")
    if _is_http_url(root_url):
        yield {
            "url": root_url,
            "protocol": info.get("protocol"),
            "ext": info.get("ext"),
            "vcodec": info.get("vcodec"),
            "acodec": info.get("acodec"),
            "format_id": info.get("format_id"),
            "http_headers": _to_headers(info.get("http_headers")),
        }

    for key in ("requested_downloads", "requested_formats", "formats"):
        for fmt in info.get(key) or []:
            if not isinstance(fmt, dict):
                continue
            if not _is_http_url(fmt.get("url")):
                continue
            yield {
                "url": fmt.get("url"),
                "protocol": fmt.get("protocol"),
                "ext": fmt.get("ext"),
                "vcodec": fmt.get("vcodec"),
                "acodec": fmt.get("acodec"),
                "format_id": fmt.get("format_id"),
                "http_headers": _to_headers(fmt.get("http_headers")),
            }


def _score_candidate(candidate: Dict[str, Any]) -> int:
    score = 0
    ext = str(candidate.get("ext") or "").lower()
    vcodec = str(candidate.get("vcodec") or "")
    acodec = str(candidate.get("acodec") or "")
    protocol = str(candidate.get("protocol") or "").lower()

    if ext == "mp4":
        score += 50
    if protocol.startswith("http"):
        score += 20
    if vcodec and vcodec != "none":
        score += 20
    if acodec and acodec != "none":
        score += 10
    return score


def _pick_candidate(info: Dict[str, Any]) -> Dict[str, Any]:
    candidates = list(_iter_candidates(info))
    if not candidates:
        raise RuntimeError("yt-dlp output did not include a playable stream url")
    return sorted(candidates, key=_score_candidate, reverse=True)[0]


def _extract_options(logger: _CollectorLogger) -> Dict[str, Any]:
    return {
        "quiet": True,
        "noprogress": True,
        "no_warnings": False,
        "noplaylist": True,
        "extract_flat": False,
        "skip_download": True,
        "allow_unplayable_formats": False,
        "prefer_ffmpeg": False,
        "hls_prefer_native": True,
        "youtube_include_dash_manifest": False,
        "cachedir": False,
        "check_formats": False,
        # Android-safe: disable js runtime probing to avoid subprocess usage on Android.
        "js_runtimes": {},
        "remote_components": set(),
        "extractor_args": {
            "youtube": {
                "player_client": ["android"],
                "player_skip": ["webpage", "configs", "js"],
            },
        },
        "format": (
            "best[protocol^=http][vcodec!=none][acodec!=none][ext=mp4]/"
            "best[protocol^=http][vcodec!=none][acodec!=none]/"
            "best"
        ),
        "http_headers": {
            "User-Agent": _DEFAULT_USER_AGENT,
            "Accept-Language": "en-US,en;q=0.9",
        },
        "logger": logger,
    }


def _parse_custom_options(ytdlp_command: str) -> Dict[str, Any]:
    argv = shlex.split(ytdlp_command)
    if argv and argv[0].strip().lower() in {"yt-dlp", "yt_dlp"}:
        argv = argv[1:]
    if not argv:
        return {}

    parsed = yt_dlp.parse_options(argv)
    return dict(parsed.ydl_opts or {})


def _build_options(logger: _CollectorLogger, ytdlp_command: Optional[str]) -> Dict[str, Any]:
    base = _extract_options(logger)
    command = (ytdlp_command or "").strip()
    if not command:
        return base

    custom = _parse_custom_options(command)
    merged = dict(base)
    merged.update(custom)

    headers = dict(base.get("http_headers") or {})
    headers.update(_to_headers(custom.get("http_headers")))
    merged["http_headers"] = headers

    extractor_args = custom.get("extractor_args")
    if isinstance(extractor_args, dict):
        merged_extractor_args = dict(base.get("extractor_args") or {})
        merged_extractor_args.update(extractor_args)
        merged["extractor_args"] = merged_extractor_args
    else:
        merged["extractor_args"] = base.get("extractor_args")

    # Always enforce extraction-safe behavior on Android.
    merged.update(
        {
            "quiet": True,
            "noprogress": True,
            "no_warnings": False,
            "noplaylist": True,
            "extract_flat": False,
            "skip_download": True,
            "allow_unplayable_formats": False,
            "prefer_ffmpeg": False,
            "hls_prefer_native": True,
            "youtube_include_dash_manifest": False,
            "cachedir": False,
            "check_formats": False,
            "js_runtimes": {},
            "remote_components": set(),
            "logger": logger,
        }
    )
    return merged


def _extract_info(page_url: str, options: Dict[str, Any], logger: _CollectorLogger) -> Dict[str, Any]:
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(page_url, download=False)
    except Exception as err:
        detail = f"{type(err).__name__}: {err}"
        logs = " | ".join(logger.messages[-3:])
        if logs:
            detail = f"{detail} | {logs}"
        raise RuntimeError(f"yt-dlp extraction failed: {detail}")

    if isinstance(info, dict) and info.get("entries"):
        entries = [entry for entry in info.get("entries") or [] if entry]
        if entries:
            info = entries[0]

    if not isinstance(info, dict):
        raise RuntimeError("yt-dlp returned unexpected payload type")
    return info


def extract(page_url: str, ytdlp_command: Optional[str] = None) -> str:
    if not _is_http_url(page_url):
        raise RuntimeError("yt-dlp extraction failed: page url must be http(s)")

    logger = _CollectorLogger()
    normalized_command = (ytdlp_command or "").strip()
    if normalized_command:
        try:
            info = _extract_info(page_url, _build_options(logger, normalized_command), logger)
        except (Exception, SystemExit) as custom_err:
            logger.warning(
                "custom ytdlpCommand failed, falling back to defaults: "
                f"{type(custom_err).__name__}: {custom_err}"
            )
            info = _extract_info(page_url, _extract_options(logger), logger)
    else:
        info = _extract_info(page_url, _extract_options(logger), logger)

    candidate = _pick_candidate(info)
    stream_url = str(candidate.get("url"))
    headers = candidate.get("http_headers") or _to_headers(info.get("http_headers"))
    headers.setdefault("User-Agent", _DEFAULT_USER_AGENT)
    headers.setdefault("Referer", page_url)

    duration = info.get("duration")
    duration_seconds = None
    if isinstance(duration, (int, float)) and duration >= 0:
        duration_seconds = int(duration)

    payload = {
        "id": str(info.get("id") or page_url),
        "title": str(info.get("title") or "Untitled"),
        "pageUrl": str(info.get("webpage_url") or page_url),
        "streamUrl": stream_url,
        "requestHeaders": headers,
        "thumbnailUrl": info.get("thumbnail"),
        "authorName": info.get("uploader"),
        "extractor": info.get("extractor"),
        "formatId": candidate.get("format_id"),
        "ext": candidate.get("ext"),
        "protocol": candidate.get("protocol"),
        "durationSeconds": duration_seconds,
        "ytDlpVersion": YTDLP_VERSION,
        "diagnostics": logger.messages[-10:],
        "resolvedAtEpochMs": int(time.time() * 1000),
    }

    return json.dumps(payload, ensure_ascii=False)
