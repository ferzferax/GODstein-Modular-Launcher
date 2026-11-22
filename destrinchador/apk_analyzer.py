import json
import os
import re
import zipfile
from datetime import datetime


TELEMETRY_KEYWORDS = [
    "INTERNET",
    "AD_ID",
    "ACCESS_ADSERVICES",
    "FIREBASE",
    "CRASHLYTICS",
    "ANALYTICS",
    "ADJUST",
    "APPSFLYER",
    "MIXEDPANEL",
    "AMPLITUDE",
    "TELEMETRY",
    "READ_BASIC_PHONE_STATE",
]

FEATURE_KEYWORDS = {
    "holo_3d": ["holo", "sphere", "3d", "orbit", "fibonacci", "glsurface", "opengl"],
    "dock": ["dock", "hotseat", "bottombar"],
    "search_autolaunch": ["autolaunch", "auto_launch", "instantsearch", "search_input", "textwatcher"],
    "gestures": ["gesture", "swipe", "doubletap", "double_tap", "fling"],
    "app_drawer": ["drawer", "appdrawer", "allapps", "alphabet", "a-z"],
    "privacy_blocker": ["adblock", "dns", "vpn", "filter", "blockads", "notifilter"],
}


def extract_strings_from_bytes(data: bytes):
    u16 = [
        m.group(0).decode("utf-16le", errors="ignore")
        for m in re.finditer(rb"(?:[\x20-\x7e]\x00){4,}", data)
    ]
    u8 = [
        m.group(0).decode("ascii", errors="ignore")
        for m in re.finditer(rb"[\x20-\x7e]{4,}", data)
    ]
    return u16 + u8


def analyze_apk(apk_path: str, output_dir: str = None) -> dict:
    if not os.path.exists(apk_path):
        raise FileNotFoundError(f"APK nao encontrado: {apk_path}")

    apk_name = os.path.basename(apk_path)
    report = {
        "file_name": apk_name,
        "file_path": os.path.abspath(apk_path),
        "size_bytes": os.path.getsize(apk_path),
        "analyzed_at": datetime.now().isoformat(),
        "package_hint": "",
        "permissions": [],
        "telemetry_flags": [],
        "detected_features": {},
        "layouts_count": 0,
        "dex_count": 0,
        "recommended_patches": [],
    }

    with zipfile.ZipFile(apk_path, "r") as z:
        names = z.namelist()
        report["layouts_count"] = sum(1 for n in names if n.startswith("res/layout"))
        dex_files = [n for n in names if n.endswith(".dex")]
        report["dex_count"] = len(dex_files)

        # Analise do AndroidManifest.xml binario
        if "AndroidManifest.xml" in names:
            manifest_data = z.read("AndroidManifest.xml")
            m_strings = extract_strings_from_bytes(manifest_data)
            perms = sorted({s for s in m_strings if "permission." in s or s.startswith("android.permission.")})
            report["permissions"] = perms

            # Detecta telemetria/bloat
            t_flags = []
            for s in m_strings:
                upper = s.upper()
                for kw in TELEMETRY_KEYWORDS:
                    if kw in upper and s not in t_flags:
                        t_flags.append(s)
            report["telemetry_flags"] = sorted(t_flags)[:30]

            # Heuristica para package name
            pkg_candidates = [
                s for s in m_strings
                if re.match(r"^[a-z][a-z0-9_]*(\.[a-z0-9_]+){1,4}$", s)
                and not s.startswith("android.")
                and not s.startswith("androidx.")
                and not s.startswith("com.google.")
                and "permission" not in s
            ]
            if pkg_candidates:
                report["package_hint"] = pkg_candidates[0]

        # Analise dos arquivos DEX para mapear funcionalidades
        feature_hits = {k: [] for k in FEATURE_KEYWORDS}
        for dex in dex_files[:3]:
            dex_data = z.read(dex)
            # Busca termos em ascii
            ascii_tokens = {
                m.group(0).decode("ascii", errors="ignore")
                for m in re.finditer(rb"[a-zA-Z][a-zA-Z0-9_\.]{3,50}", dex_data)
            }
            for feat, kws in FEATURE_KEYWORDS.items():
                for tok in ascii_tokens:
                    low = tok.lower()
                    if any(kw in low for kw in kws):
                        if len(feature_hits[feat]) < 12 and tok not in feature_hits[feat]:
                            feature_hits[feat].append(tok)

        report["detected_features"] = {k: v for k, v in feature_hits.items() if v}

    # Sugestoes de patches para o acervo
    if report["telemetry_flags"]:
        report["recommended_patches"].append("debloat-zero-telemetry")
    if "holo_3d" in report["detected_features"]:
        report["recommended_patches"].append("holo-3d-sphere")
    if "dock" in report["detected_features"]:
        report["recommended_patches"].append("clean-bottom-dock")
    if "search_autolaunch" in report["detected_features"]:
        report["recommended_patches"].append("instant-autolaunch-drawer")
    if "gestures" in report["detected_features"]:
        report["recommended_patches"].append("dragon-grace-mink-gestures")

    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
        safe_name = re.sub(r"[^a-zA-Z0-9._-]", "_", apk_name) + ".json"
        out_path = os.path.join(output_dir, safe_name)
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        report["report_path"] = out_path

    return report
