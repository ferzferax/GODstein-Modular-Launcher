import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime

STUDIO_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(STUDIO_DIR, "destrinchador"))
from apk_analyzer import analyze_apk  # noqa: E402

ACERVO_DIR = os.path.join(STUDIO_DIR, "acervo")
CATALOG_PATH = os.path.join(ACERVO_DIR, "patches_catalog.json")
ANALYZED_DIR = os.path.join(ACERVO_DIR, "analyzed_apks")
SKELETON_DIR = os.path.join(STUDIO_DIR, "skeleton")
MORPHE_DIR = os.path.join(STUDIO_DIR, "morphe_bundle")
DIST_DIR = os.path.join(STUDIO_DIR, "dist")
BUILD_DIR = os.path.join(STUDIO_DIR, "build")
TOOLS_DIR = os.path.join(STUDIO_DIR, "tools")

DESKTOP_DIR = os.path.join(os.path.expanduser("~"), "Desktop")
APKS_DESKTOP_DIR = os.path.join(DESKTOP_DIR, "APKs")

JAVA_EXE = r"C:\Users\ferna\AppData\Local\Programs\jdk-17\bin\java.exe"
JAVAC_EXE = r"C:\Users\ferna\AppData\Local\Programs\jdk-17\bin\javac.exe"
AAPT2_EXE = r"C:\Users\ferna\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe"
D8_JAR = r"C:\Users\ferna\AppData\Local\Android\Sdk\build-tools\36.0.0\lib\d8.jar"
ANDROID_JAR = r"C:\Users\ferna\AppData\Local\Android\Sdk\platforms\android-36\android.jar"

BRAIN_SCRATCH = r"C:\Users\ferna\.gemini\antigravity\brain\bbc3d757-ab75-4039-9f5c-5f57229011b3\scratch"


def get_tool_jar(name: str) -> str:
    local_path = os.path.join(TOOLS_DIR, name)
    if os.path.exists(local_path):
        return local_path
    scratch_path = os.path.join(BRAIN_SCRATCH, name)
    if os.path.exists(scratch_path):
        os.makedirs(TOOLS_DIR, exist_ok=True)
        shutil.copy2(scratch_path, local_path)
        return local_path
    raise FileNotFoundError(f"Ferramenta nao encontrada: {name}")


def load_catalog() -> dict:
    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_catalog(catalog: dict):
    with open(CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)


def cmd_list_patches():
    cat = load_catalog()
    print(f"\n=== {cat['studio_name']} v{cat['version']} | Curador: @{cat['curator']} ===")
    for p in cat["patches"]:
        state = "ON " if p.get("enabled") else "OFF"
        print(f"  [{state}] {p['id']} | {p['name']}")
        print(f"        Origem: {p['origin_app']} ({p['github_url']})")
        print(f"        {p['description']}")
    print()


def cmd_toggle_patch(patch_id: str):
    cat = load_catalog()
    for p in cat["patches"]:
        if p["id"] == patch_id:
            p["enabled"] = not p.get("enabled", True)
            save_catalog(cat)
            state = "ATIVADO" if p["enabled"] else "DESATIVADO"
            print(f"Patch '{patch_id}' agora esta {state}.")
            return
    print(f"Patch nao encontrado: {patch_id}")


def cmd_add_patch(patch_id: str, name: str, origin_app: str, author: str, github_url: str, description: str):
    cat = load_catalog()
    for p in cat["patches"]:
        if p["id"] == patch_id:
            print(f"Ja existe um patch com ID '{patch_id}'. Atualizando metadados...")
            p.update({
                "name": name,
                "origin_app": origin_app,
                "author": author,
                "github_url": github_url,
                "description": description,
                "enabled": True,
            })
            save_catalog(cat)
            return

    cat["patches"].append({
        "id": patch_id,
        "name": name,
        "description": description,
        "enabled": True,
        "category": "custom_module",
        "origin_app": origin_app,
        "author": author,
        "github_url": github_url,
        "license": "Open Source",
        "morphe_compatible": True,
        "config_key": "CUSTOM_" + patch_id.upper().replace("-", "_"),
    })
    save_catalog(cat)
    print(f"Patch '{patch_id}' adicionado ao acervo com sucesso!")


def cmd_analyze(apk_path: str):
    print(f"Destrinchando APK: {apk_path} ...")
    rep = analyze_apk(apk_path, ANALYZED_DIR)
    print(f"  Pacote estimado : {rep['package_hint'] or '(nao identificado)'}")
    print(f"  Permissoes ({len(rep['permissions'])}): {', '.join(rep['permissions'][:6])}{'...' if len(rep['permissions']) > 6 else ''}")
    print(f"  Flags telemetria: {len(rep['telemetry_flags'])}")
    print(f"  Features detect.: {list(rep['detected_features'].keys())}")
    print(f"  Patches sugeridos: {rep['recommended_patches']}")
    print(f"  Relatorio salvo : {rep.get('report_path')}\n")
    return rep


def cmd_analyze_all():
    if not os.path.exists(APKS_DESKTOP_DIR):
        print(f"Pasta nao encontrada: {APKS_DESKTOP_DIR}")
        return
    apks = [
        os.path.join(APKS_DESKTOP_DIR, f)
        for f in os.listdir(APKS_DESKTOP_DIR)
        if f.lower().endswith(".apk") and "godstein" not in f.lower()
    ]
    print(f"Encontrados {len(apks)} APKs em {APKS_DESKTOP_DIR} para destrinchar...\n")
    for apk in sorted(apks):
        try:
            cmd_analyze(apk)
        except Exception as e:
            print(f"  Erro ao analisar {apk}: {e}\n")


def java_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", " ")


def generate_build_config(cat: dict):
    flags = {
        "ENABLE_HOLO_SPHERE": True,
        "ENABLE_CLEAN_DOCK": True,
        "ENABLE_AUTO_LAUNCH_SEARCH": True,
        "ENABLE_QUICK_GESTURES": True,
        "ENABLE_ZERO_TELEMETRY": True,
    }
    credits_lines = []
    for p in cat["patches"]:
        key = p.get("config_key", "")
        if key in flags:
            flags[key] = bool(p.get("enabled", True))
        if p.get("enabled", True):
            credits_lines.append(
                f'        list.add(new CreditEntry(\n'
                f'            "{java_escape(p["name"])}",\n'
                f'            "{java_escape(p["origin_app"])}",\n'
                f'            "{java_escape(p["author"])}",\n'
                f'            "{java_escape(p["github_url"])}",\n'
                f'            "{java_escape(p["description"])}"\n'
                f'        ));'
            )
            if p.get("secondary_github_url"):
                credits_lines.append(
                    f'        list.add(new CreditEntry(\n'
                    f'            "{java_escape(p["name"])} (Projeto Associado)",\n'
                    f'            "{java_escape(p["origin_app"])}",\n'
                    f'            "{java_escape(p["author"])}",\n'
                    f'            "{java_escape(p["secondary_github_url"])}",\n'
                    f'            "{java_escape(p["description"])}"\n'
                    f'        ));'
                )

    java_src = f"""package com.ferzferaz.godstein;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuracao modular gerada automaticamente pelo GODstein Studio em {datetime.now().isoformat()}.
 */
public final class BuildConfigPatches {{
    public static final String STUDIO_VERSION = "{java_escape(cat['version'])}";
    public static final String CURATOR_USER = "{java_escape(cat['curator'])}";
    public static final String CURATOR_GITHUB = "{java_escape(cat['curator_github'])}";

    public static final boolean ENABLE_HOLO_SPHERE = {"true" if flags["ENABLE_HOLO_SPHERE"] else "false"};
    public static final boolean ENABLE_CLEAN_DOCK = {"true" if flags["ENABLE_CLEAN_DOCK"] else "false"};
    public static final boolean ENABLE_AUTO_LAUNCH_SEARCH = {"true" if flags["ENABLE_AUTO_LAUNCH_SEARCH"] else "false"};
    public static final boolean ENABLE_QUICK_GESTURES = {"true" if flags["ENABLE_QUICK_GESTURES"] else "false"};
    public static final boolean ENABLE_ZERO_TELEMETRY = {"true" if flags["ENABLE_ZERO_TELEMETRY"] else "false"};

    public static final class CreditEntry {{
        public final String moduleName;
        public final String originApp;
        public final String author;
        public final String githubUrl;
        public final String description;

        public CreditEntry(String moduleName, String originApp, String author, String githubUrl, String description) {{
            this.moduleName = moduleName;
            this.originApp = originApp;
            this.author = author;
            this.githubUrl = githubUrl;
            this.description = description;
        }}
    }}

    public static List<CreditEntry> getActiveCredits() {{
        List<CreditEntry> list = new ArrayList<>();
{chr(10).join(credits_lines)}
        return list;
    }}
}}
"""
    target = os.path.join(SKELETON_DIR, "src", "com", "ferzferaz", "godstein", "BuildConfigPatches.java")
    with open(target, "w", encoding="utf-8") as f:
        f.write(java_src)
    print(f"Gerado BuildConfigPatches.java com {len(credits_lines)} entradas de creditos.")


def export_morphe_bundle(cat: dict):
    os.makedirs(MORPHE_DIR, exist_ok=True)
    bundle = {
        "schema": "morphe-patches-bundle/v1",
        "bundleName": "GODstein Modular Launcher Patches",
        "author": cat["curator"],
        "authorGithub": cat["curator_github"],
        "generatedAt": datetime.now().isoformat(),
        "compatiblePackages": [
            {"name": cat["skeleton_package"], "versions": ["1.0.0-godstein"]},
            {"name": "com.atlantis.launcher", "versions": ["any"]},
        ],
        "patches": [],
    }

    for p in cat["patches"]:
        patch_obj = {
            "name": p["name"],
            "id": p["id"],
            "description": p["description"],
            "use": bool(p.get("enabled", True)),
            "origin": {
                "app": p["origin_app"],
                "author": p["author"],
                "github": p["github_url"],
            },
            "compatiblePackages": (
                [p["target_package"]] if p.get("target_package") else [cat["skeleton_package"]]
            ),
        }
        if p["id"] == "dna-holo-safe-patch":
            patch_obj["smaliHook"] = {
                "targetClass": "Lkm4;",
                "targetMethod": "O0()V",
                "fixNote": "Usar setAlpha(0.0f) + setTranslationY(500.0f) + setClickable(false) em vez de View.GONE para evitar textura 0x0 no FrameLayoutOnBlur.",
            }
        bundle["patches"].append(patch_obj)

    out_file = os.path.join(MORPHE_DIR, "godstein-morphe-patches.json")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(bundle, f, indent=2, ensure_ascii=False)
    print(f"Bundle Morphe exportado: {out_file}")


def run_step(cmd: list, label: str):
    print(f"[{label}] Executando...")
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if proc.returncode != 0:
        print(proc.stdout)
        raise RuntimeError(f"Falha na etapa '{label}' (codigo {proc.returncode}).")
    if proc.stdout.strip():
        print(proc.stdout.strip())


def cmd_build():
    cat = load_catalog()
    generate_build_config(cat)
    export_morphe_bundle(cat)

    if os.path.exists(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    gen_dir = os.path.join(BUILD_DIR, "gen")
    classes_dir = os.path.join(BUILD_DIR, "classes")
    dex_dir = os.path.join(BUILD_DIR, "dex")
    signed_dir = os.path.join(BUILD_DIR, "signed")
    for d in [gen_dir, classes_dir, dex_dir, signed_dir, DIST_DIR]:
        os.makedirs(d, exist_ok=True)

    res_zip = os.path.join(BUILD_DIR, "res_compiled.zip")
    base_apk = os.path.join(BUILD_DIR, "base_linked.apk")
    unsigned_apk = os.path.join(BUILD_DIR, "GODstein_unsigned.apk")

    # 1. aapt2 compile
    run_step(
        [AAPT2_EXE, "compile", "--dir", os.path.join(SKELETON_DIR, "res"), "-o", res_zip],
        "aapt2 compile",
    )

    # 2. aapt2 link
    run_step(
        [
            AAPT2_EXE,
            "link",
            "-o",
            base_apk,
            "--manifest",
            os.path.join(SKELETON_DIR, "AndroidManifest.xml"),
            "-I",
            ANDROID_JAR,
            "--java",
            gen_dir,
            "--min-sdk-version",
            "26",
            "--target-sdk-version",
            "36",
            res_zip,
        ],
        "aapt2 link",
    )

    # 3. javac
    java_files = []
    for root_src in [os.path.join(SKELETON_DIR, "src"), gen_dir]:
        for r, _, files in os.walk(root_src):
            for fn in files:
                if fn.endswith(".java"):
                    java_files.append(os.path.join(r, fn))

    sources_txt = os.path.join(BUILD_DIR, "sources.txt")
    with open(sources_txt, "w", encoding="utf-8") as f:
        for jf in java_files:
            jf_posix = jf.replace("\\", "/")
            f.write(f'"{jf_posix}"\n')

    run_step(
        [
            JAVAC_EXE,
            "--release",
            "11",
            "-encoding",
            "UTF-8",
            "-cp",
            ANDROID_JAR,
            "-d",
            classes_dir,
            f"@{sources_txt}",
        ],
        "javac",
    )

    # 4. d8
    class_files = []
    for r, _, files in os.walk(classes_dir):
        for fn in files:
            if fn.endswith(".class"):
                class_files.append(os.path.join(r, fn))

    run_step(
        [
            JAVA_EXE,
            "-cp",
            D8_JAR,
            "com.android.tools.r8.D8",
            "--min-api",
            "26",
            "--lib",
            ANDROID_JAR,
            "--output",
            dex_dir,
        ]
        + class_files,
        "d8 dexing",
    )

    # 5. Merge classes.dex into APK preservando STORE para resources.arsc
    classes_dex = os.path.join(dex_dir, "classes.dex")
    with zipfile.ZipFile(base_apk, "r") as zin, zipfile.ZipFile(unsigned_apk, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            new_info = zipfile.ZipInfo(info.filename)
            new_info.compress_type = info.compress_type
            zout.writestr(new_info, data)
        zout.write(classes_dex, "classes.dex", compress_type=zipfile.ZIP_DEFLATED)

    # 6. Sign with uber-apk-signer
    uber_jar = get_tool_jar("uber-apk-signer.jar")
    run_step(
        [JAVA_EXE, "-jar", uber_jar, "-a", unsigned_apk, "-o", signed_dir],
        "uber-apk-signer",
    )

    signed_candidates = glob.glob(os.path.join(signed_dir, "*.apk"))
    if not signed_candidates:
        raise RuntimeError("APK assinado nao encontrado apos uber-apk-signer.")
    signed_apk = signed_candidates[0]

    targets = [
        os.path.join(DIST_DIR, "GODstein_Launcher.apk"),
        os.path.join(DESKTOP_DIR, "GODstein_Launcher.apk"),
    ]
    if os.path.exists(APKS_DESKTOP_DIR):
        targets.append(os.path.join(APKS_DESKTOP_DIR, "GODstein_Launcher.apk"))

    for t in targets:
        shutil.copy2(signed_apk, t)
        print(f"APK gerado: {t} ({os.path.getsize(t)} bytes)")

    return targets[0]


def cmd_install():
    apk_path = os.path.join(DESKTOP_DIR, "GODstein_Launcher.apk")
    if not os.path.exists(apk_path):
        apk_path = cmd_build()

    out = subprocess.check_output(["adb", "devices"], text=True)
    lines = [l.strip() for l in out.splitlines()[1:] if l.strip() and "\tdevice" in l]
    if not lines:
        print("Nenhum dispositivo ADB conectado. Tentando conectar 192.168.1.114:39481...")
        subprocess.run(["adb", "connect", "192.168.1.114:39481"])
        out = subprocess.check_output(["adb", "devices"], text=True)
        lines = [l.strip() for l in out.splitlines()[1:] if l.strip() and "\tdevice" in l]
        if not lines:
            raise RuntimeError("Nenhum aparelho conectado via ADB.")

    serial = lines[0].split("\t")[0]
    print(f"Instalando {apk_path} em {serial}...")
    run_step(["adb", "-s", serial, "install", "-r", apk_path], "adb install")
    run_step(
        ["adb", "-s", serial, "shell", "am", "start", "-n", "com.ferzferaz.godstein/.MainActivity"],
        "launch GODstein",
    )


def interactive_menu():
    while True:
        print("\n===================================================")
        print("  GODstein Modular Launcher Studio (@ferzferax)")
        print("===================================================")
        print("  1. Listar patches do acervo")
        print("  2. Ativar / Desativar um patch")
        print("  3. Destrinchar um novo APK (adicionar ao acervo)")
        print("  4. Destrinchar todos APKs da pasta Desktop\\APKs")
        print("  5. Montar e Assinar GODstein_Launcher.apk (+ Morphe)")
        print("  6. Instalar GODstein_Launcher.apk no celular via ADB")
        print("  0. Sair")
        choice = input("\nEscolha uma opcao: ").strip()
        if choice == "1":
            cmd_list_patches()
        elif choice == "2":
            pid = input("Digite o ID do patch (ex: holo-3d-sphere): ").strip()
            cmd_toggle_patch(pid)
        elif choice == "3":
            path = input("Caminho completo do APK: ").strip().strip('"')
            cmd_analyze(path)
        elif choice == "4":
            cmd_analyze_all()
        elif choice == "5":
            cmd_build()
        elif choice == "6":
            cmd_install()
        elif choice == "0":
            break
        else:
            print("Opcao invalida.")


def main():
    parser = argparse.ArgumentParser(description="GODstein Modular Launcher Studio")
    parser.add_argument("--list-patches", action="store_true", help="Lista patches do acervo")
    parser.add_argument("--toggle", metavar="PATCH_ID", help="Ativa/desativa um patch")
    parser.add_argument("--analyze", metavar="APK_PATH", help="Destrincha um APK")
    parser.add_argument("--analyze-all", action="store_true", help="Destrincha todos os APKs de Desktop/APKs")
    parser.add_argument("--build", action="store_true", help="Compila e assina o GODstein_Launcher.apk")
    parser.add_argument("--install", action="store_true", help="Instala GODstein_Launcher.apk no celular")
    args = parser.parse_args()

    if args.list_patches:
        cmd_list_patches()
    elif args.toggle:
        cmd_toggle_patch(args.toggle)
    elif args.analyze:
        cmd_analyze(args.analyze)
    elif args.analyze_all:
        cmd_analyze_all()
    elif args.build:
        cmd_build()
    elif args.install:
        cmd_install()
    else:
        interactive_menu()


if __name__ == "__main__":
    main()
