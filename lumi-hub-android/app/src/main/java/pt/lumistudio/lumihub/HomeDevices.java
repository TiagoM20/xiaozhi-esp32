package pt.lumistudio.lumihub;

/**
 * Registo unico de equipamentos visiveis na Estacao e em Dispositivos.
 * Acrescentar aqui novos equipamentos permite manter os dois ecras coerentes.
 * "Por configurar" nunca deve significar "online".
 */
public final class HomeDevices {
    public static final class Device {
        public final String key;
        public final String name;
        public final String subtitle;
        public final String group;
        public final String icon;

        private Device(String key, String name, String subtitle, String group, String icon) {
            this.key=key;
            this.name=name;
            this.subtitle=subtitle;
            this.group=group;
            this.icon=icon;
        }
    }

    public static final Device[] ALL = {
        new Device("lumi", "LUMI DESK", "Assistente de voz", "Casa", "lumi"),
        new Device("tv", "TV da Sala", "LG webOS", "Casa", "tv"),
        new Device("camera", "Câmara Varanda", "IPC-TA22C-G", "Casa", "camera"),
        new Device("vacuum", "Aspirador", "Alfawise · a configurar", "Casa", "vacuum"),
        new Device("tablet", "Tab 15", "Central doméstica", "Casa", "tablet"),
        new Device("phone_deolinda", "Telemóvel Deolinda", "Google Find Hub", "Telemóveis", "phone"),
        new Device("phone_tiago", "Telemóvel Tiago", "Google Find Hub", "Telemóveis", "phone"),
        new Device("phone_leonardo", "Telemóvel Leonardo", "Google Find Hub", "Telemóveis", "phone"),
        new Device("spotify", "Spotify", "Música", "Serviços", "music"),
        new Device("ac", "Ar condicionado", "Sala · a configurar", "Casa", "ac"),
        new Device("plug", "Tomada Wi-Fi", "Por adicionar", "Casa", "plug"),
        new Device("more", "Mais dispositivos", "Espaço para expandir", "Serviços", "plus")
    };

    private HomeDevices() {}
}
