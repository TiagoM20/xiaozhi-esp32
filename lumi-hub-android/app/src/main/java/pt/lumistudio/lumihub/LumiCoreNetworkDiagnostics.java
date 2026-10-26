package pt.lumistudio.lumihub;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Diagnóstico local da ligação LUMI CORE, sem rede externa nem chaves. */
final class LumiCoreNetworkDiagnostics {
    private LumiCoreNetworkDiagnostics() {}

    static String report(Context context) {
        SharedPreferences prefs=context.getApplicationContext()
            .getSharedPreferences("hub", Context.MODE_PRIVATE);
        boolean enabled=prefs.getBoolean("lumi_core_sync_enabled",false);
        boolean reported=prefs.getBoolean("lumi_core_sync_running",false);
        String ip=LumiCoreSyncServer.localIp();
        List<String> addresses=new ArrayList<>();
        try {
            for(NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if(!nic.isUp() || nic.isLoopback())continue;
                for(InetAddress address:Collections.list(nic.getInetAddresses())) {
                    if(address instanceof Inet4Address && !address.isLoopbackAddress())
                        addresses.add(nic.getName()+" = "+address.getHostAddress());
                }
            }
        }catch(Exception ignored) {
            addresses.add("Não foi possível listar interfaces locais.");
        }

        boolean portOpen=false;
        String reason="";
        try(Socket probe=new Socket()) {
            // Verifica somente se existe um listener TCP local. Nao envia dados,
            // nunca transmite o codigo de emparelhamento nem credenciais.
            probe.connect(new InetSocketAddress("127.0.0.1",8770),1000);
            portOpen=true;
        }catch(Exception error) {
            reason=error.getClass().getSimpleName();
        }

        StringBuilder out=new StringBuilder();
        out.append("LUMI CORE · Diagnóstico local\n");
        out.append("Ligação Windows autorizada: ").append(enabled?"SIM":"NÃO").append("\n");
        out.append("Servidor informado pelo HUB: ").append(reported?"ATIVO":"PARADO").append("\n");
        out.append("Teste TCP no próprio tablet: ").append(portOpen?"PORTA 8770 ABERTA":"PORTA 8770 FECHADA");
        if(!portOpen && !reason.isEmpty())out.append(" (").append(reason).append(")");
        out.append("\n");
        out.append("IP que aparece no HUB: ").append(ip==null?"indisponível":ip).append("\n");
        out.append("Interfaces IPv4 locais:\n");
        if(addresses.isEmpty())out.append("  Nenhum endereço IPv4 ativo identificado.\n");
        else for(String address:addresses)out.append("  ").append(address).append("\n");
        out.append("\n");
        if(!enabled) {
            out.append("AÇÃO: ativa a ligação ao Windows no tablet antes de emparelhar.");
        }else if(!portOpen) {
            out.append("AÇÃO: inicia a central LUMI HUB; confirma que a notificação "+
                "de serviço está ativa. Fecha e reabre apenas o HUB se necessário.");
        }else if(ip==null || ip.isEmpty()) {
            out.append("AÇÃO: liga o tablet ao Wi-Fi doméstico e confirma o novo endereço IP.");
        }else {
            out.append("A porta do HUB funciona localmente. Se o CORE Windows "+
                "continuar a mostrar TEMPO ESGOTADO, verifica que PC e tablet "+
                "comunicam na mesma rede, que não estão numa rede de convidados "+
                "e que não existe isolamento de clientes ou VPN. "+
                "Este teste não confirma acesso a partir do PC.");
        }
        out.append("\n\nNenhuma chave, token, conversa ou palavra-passe está incluída.");
        return out.toString();
    }
}
