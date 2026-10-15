package pt.lumistudio.lumihub;

import android.graphics.Color;

/**
 * Dez temas locais do LUMI Hub. Nenhuma alteracao depende da rede,
 * os temas ficam persistidos apenas em SharedPreferences.
 */
public final class HubThemes {
    public static final String[] NAMES = {
        "LUMI Azul", "Escuro", "Claro", "Colorido", "Aurora",
        "Areia", "Floresta", "Rosa Névoa", "Terracota", "Minimal"
    };
    public static final class Palette {
        public final int background, panel, bright, text, muted, accent, gold, violet, green, stroke;
        public final int nav, bannerA, bannerB, bannerC, tileA, tileB, tileC;
        public final int tileText, tileIcon, tileBorder, statusPanel, activeTab;
        private Palette(String bg, String panel, String bright, String text,
                        String muted, String accent, String gold, String violet,
                        String green, String stroke, String nav,
                        String bannerA, String bannerB, String bannerC,
                        String tileA, String tileB, String tileC,
                        String tileText, String tileIcon, String tileBorder,
                        String statusPanel, String activeTab) {
            this.background=color(bg); this.panel=color(panel); this.bright=color(bright);
            this.text=color(text); this.muted=color(muted); this.accent=color(accent);
            this.gold=color(gold); this.violet=color(violet); this.green=color(green);
            this.stroke=color(stroke); this.nav=color(nav);
            this.bannerA=color(bannerA); this.bannerB=color(bannerB);
            this.bannerC=color(bannerC); this.tileA=color(tileA);
            this.tileB=color(tileB); this.tileC=color(tileC);
            this.tileText=color(tileText); this.tileIcon=color(tileIcon);
            this.tileBorder=color(tileBorder); this.statusPanel=color(statusPanel);
            this.activeTab=color(activeTab);
        }
    }
    private static int color(String c) { return Color.parseColor(c); }
    public static int bounded(int i) { return i >= 0 && i < NAMES.length ? i : 0; }
    public static Palette get(int i) {
        switch (bounded(i)) {
            case 1: // escuro neutro, sem saturacao
                return new Palette("#080B12","#1C212B","#303745","#F4F6FA",
                    "#ABB4C5","#88B6FF","#FFD390","#C3B0FF","#92DABE",
                    "#495262","#10151F","#242B36","#161C29","#0C101B",
                    "#2A303C","#1B222D","#151C29","#FFFFFF","#FFFFFF",
                    "#444F61","#222937","#323E5D");
            case 2: // claro: fundo branco e tiles azul claro
                return new Palette("#F7FAFE","#FFFFFF","#DFECFC","#142943",
                    "#51667F","#316FC1","#AC6517","#7757B5","#167757",
                    "#C5D8EC","#E7F1FF","#C7E7FF","#91CDF7","#639BD6",
                    "#D8EEFF","#BBDCF8","#A2CEF2","#163556","#174673",
                    "#9FC5E8","#ECF4FF","#BAD9F6");
            case 3: // tiles vivos
                return new Palette("#0E1424","#202C45","#33476C","#F8FAFF",
                    "#B7C9E6","#78E6FF","#FFDC8D","#D7ADFF","#A7EDBE",
                    "#516B96","#14203B","#47308B","#2A5CBD","#1CA8B8",
                    "#1F6DB1","#3C3FA4","#8A43AC","#FFFFFF","#FFFFFF",
                    "#97A8E8","#253A60","#3459A8");
            case 4: // aurora teal, roxo e tons glaciais
                return new Palette("#100F21","#221D3B","#36305A","#F9F5FF",
                    "#BBB1D8","#80E1D9","#FFD2A1","#E5B6FF","#A3EBCD",
                    "#544A78","#1D1737","#513C82","#364C91","#247B8B",
                    "#413668","#2B486E","#20556D","#FFFFFF","#FFFFFF",
                    "#7A77B6","#2A2452","#503E80");
            case 5: // Areia
                return new Palette("#F6F2EB","#FFFFFF","#EEE4D5","#362D27","#73675B",
                    "#9A694D","#B88E4D","#9D829E","#527A66","#DBCEBF",
                    "#EFE7DA","#DCCCB7","#D8B7A0","#AF886B","#E7D5C1",
                    "#D8BFA6","#C6A98E","#32251E","#573B2E","#BDAC9B",
                    "#F9F4EB","#DBCAB6");
            case 6: // Floresta
                return new Palette("#0C1917","#182D29","#23453E","#F0F7F2","#A3C3B5",
                    "#6CD9AC","#E7C678","#B9ADE8","#90D4B0","#426A5C",
                    "#102620","#244A3D","#286A51","#285747","#234F43",
                    "#1D6052","#19443A","#FFFFFF","#E6FFF1","#477C69",
                    "#143A32","#286A51");
            case 7: // Rosa Névoa
                return new Palette("#FBF7F7","#FFFFFF","#F3E4E9","#3F3039","#806D77",
                    "#AD6685","#C79755","#9876A0","#608B75","#E6D2DC",
                    "#F5E7ED","#E7C6D4","#C6ADC7","#B68B9E","#F1D9E2",
                    "#E4C3D3","#D8B3C7","#3F2733","#613D50","#D8BFCA",
                    "#F8EBF1","#E4C4D3");
            case 8: // Terracota
                return new Palette("#241916","#382823","#533C34","#FFF5E9","#D4B8A8",
                    "#EEA475","#EBC48C","#D8A3B5","#B7C9A1","#75544A",
                    "#30221D","#8C4934","#B66B48","#A78A62","#854B37",
                    "#9A5C42","#6F3F36","#FFFFFF","#FFF6EF","#A87662",
                    "#483028","#A66347");
            case 9: // Minimal
                return new Palette("#F0F3F4","#FFFFFF","#DDE5E8","#1C2C32","#607177",
                    "#477789","#BE9459","#8290A6","#518077","#C9D6DB",
                    "#E5EDF0","#AACAD4","#93B1BD","#648E9D","#CFDFE5",
                    "#B9D0D7","#ADC5CB","#162A32","#2C4A54","#A9C0CB",
                    "#E4EDF0","#BAD1D8");
            default: // painel azul v9
                return new Palette("#0C121F","#182030","#222C3E","#F5F8FF",
                    "#A2AFC3","#72E4EF","#FFBB77","#AE9AFB","#8BDFAD",
                    "#344055","#0D1B35","#203573","#1E538E","#183467",
                    "#264378","#203264","#1B254F","#FFFFFF","#FFFFFF",
                    "#3E5A8E","#1B294F","#2B5192");
        }
    }
    public static int[] tileGradient(int themeId, int index) {
        if (bounded(themeId) != 3) {
            Palette p=get(themeId);
            return new int[]{p.tileA,p.tileB,p.tileC};
        }
        int[][] tiles = {
            {0xFF7953CC,0xFF493795,0xFF2D246F},
            {0xFF259DDE,0xFF2862B7,0xFF183F7B},
            {0xFF26B5A4,0xFF217C8B,0xFF1A5272},
            {0xFFE2A13A,0xFFC2672A,0xFF804022},
            {0xFF587CE4,0xFF4554AE,0xFF28376A},
            {0xFFCB66A5,0xFF863E85,0xFF4E2A68},
            {0xFF59A793,0xFF307C8F,0xFF17536E},
            {0xFF8A91E1,0xFF615AAE,0xFF343D81},
            {0xFF38AB78,0xFF217B75,0xFF195D68},
            {0xFF6B88E5,0xFF3D67C4,0xFF24477D},
            {0xFFEEA658,0xFFA86267,0xFF70456D},
            {0xFF596E98,0xFF465A7F,0xFF304264}
        };
        return tiles[index % tiles.length];
    }
    private HubThemes() {}
}
