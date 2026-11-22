package com.ferzferaz.godstein;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuracao modular gerada automaticamente pelo GODstein Studio em 2026-11-22T02:18:53.757506.
 */
public final class BuildConfigPatches {
    public static final String STUDIO_VERSION = "1.0.0";
    public static final String CURATOR_USER = "ferzferax";
    public static final String CURATOR_GITHUB = "https://github.com/ferzferax";

    public static final boolean ENABLE_HOLO_SPHERE = true;
    public static final boolean ENABLE_CLEAN_DOCK = true;
    public static final boolean ENABLE_AUTO_LAUNCH_SEARCH = true;
    public static final boolean ENABLE_QUICK_GESTURES = true;
    public static final boolean ENABLE_ZERO_TELEMETRY = true;

    public static final class CreditEntry {
        public final String moduleName;
        public final String originApp;
        public final String author;
        public final String githubUrl;
        public final String description;

        public CreditEntry(String moduleName, String originApp, String author, String githubUrl, String description) {
            this.moduleName = moduleName;
            this.originApp = originApp;
            this.author = author;
            this.githubUrl = githubUrl;
            this.description = description;
        }
    }

    public static List<CreditEntry> getActiveCredits() {
        List<CreditEntry> list = new ArrayList<>();
        list.add(new CreditEntry(
            "Esfera Holo 3D (Sem Barra de Pesquisa)",
            "AuraOrbit",
            "JaiminPatel345",
            "https://github.com/JaiminPatel345/AuraOrbit",
            "Esfera 3D Fibonacci interativa na tela inicial com física de inércia suave, escala de profundidade e zero barra de pesquisa fixa."
        ));
        list.add(new CreditEntry(
            "Dock Limpo Inferior",
            "Arcanum",
            "Esdex",
            "https://github.com/Esdex/Arcanum",
            "Dock minimalista translúcido na parte inferior apenas com os aplicativos favoritos e botão da gaveta, sem barra de pesquisa acoplada."
        ));
        list.add(new CreditEntry(
            "Gaveta com Teclado Imediato & Auto-Launch",
            "Beam & Bluke",
            "montafra & arnav-kr",
            "https://github.com/montafra/beam",
            "Abre o teclado automaticamente ao acessar a gaveta (fix Android 16/One UI) e abre o app instantaneamente quando resta 1 correspondência ou match exato."
        ));
        list.add(new CreditEntry(
            "Gaveta com Teclado Imediato & Auto-Launch (Projeto Associado)",
            "Beam & Bluke",
            "montafra & arnav-kr",
            "https://github.com/arnav-kr/Bluke",
            "Abre o teclado automaticamente ao acessar a gaveta (fix Android 16/One UI) e abre o app instantaneamente quando resta 1 correspondência ou match exato."
        ));
        list.add(new CreditEntry(
            "Gestos Rápidos & Filtro (Dragon / Grace / Mink)",
            "Dragon / Grace / Mink Concepts",
            "ferzferax & Community",
            "https://github.com/ferzferax",
            "Deslize para cima na esfera abre a gaveta direto no teclado; filtro por inicial e menu rápido de créditos e módulos GODstein."
        ));
        list.add(new CreditEntry(
            "Zero Telemetria & Zero Permissões de Rede",
            "NotiFilter & BlockAds",
            "BURG3R5 & pass-with-high-score",
            "https://github.com/BURG3R5/NotiFilter",
            "Remove completamente permissões INTERNET, AD_ID, Firebase e rastreadores. Execução 100% offline e privada."
        ));
        list.add(new CreditEntry(
            "Zero Telemetria & Zero Permissões de Rede (Projeto Associado)",
            "NotiFilter & BlockAds",
            "BURG3R5 & pass-with-high-score",
            "https://github.com/pass-with-high-score/blockads-android",
            "Remove completamente permissões INTERNET, AD_ID, Firebase e rastreadores. Execução 100% offline e privada."
        ));
        list.add(new CreditEntry(
            "DNA Launcher Safe Holo Bar Hider (Morphe Patch)",
            "DNA Launcher (Patch)",
            "ferzferax",
            "https://github.com/ferzferax",
            "Patch seguro para o DNA Launcher externo usando setAlpha(0f) + setTranslationY(500f) + setClickable(false) em vez de View.GONE para não quebrar o shader de blur."
        ));
        return list;
    }
}
