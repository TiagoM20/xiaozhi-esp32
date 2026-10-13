package pt.lumistudio.lumihub;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Opus 16 kHz entrada da DESK; Opus 24 kHz/60 ms saída para a DESK. */
final class LumiOpus {
    private LumiOpus(){}
    static final class Decoder {
        private final OpusDecoder codec;
        Decoder() throws Exception{codec=OpusDecoder.create(16000,1);}
        short[] decode(byte[] opus) throws Exception {
            short[] pcm=new short[960];
            int n=codec.decode(opus,0,opus.length,pcm,0,960,false);
            if(n<1 || n>960)throw new IOException("Pacote Opus de entrada inválido");
            return Arrays.copyOf(pcm,n);
        }
    }
    interface PacketSink { boolean send(byte[] frame) throws Exception; }
    static int sendPcm(byte[] pcm,PacketSink sink) throws Exception {
        if(pcm.length<2)throw new IOException("Não foi gerado áudio PCM");
        OpusEncoder enc=OpusEncoder.create(24000,1,OpusApplication.OPUS_APPLICATION_VOIP);
        enc.setBitrate(32000);
        int frames=(pcm.length+2879)/2880;
        long start=android.os.SystemClock.elapsedRealtime();
        int sent=0;
        for(int i=0;i<frames;i++){
            short[] samples=new short[1440];
            int offset=i*2880;
            int available=Math.min(2880,pcm.length-offset);
            for(int j=0;j+1<available;j+=2){
                samples[j/2]=(short)((pcm[offset+j]&255)|((pcm[offset+j+1]&255)<<8));
            }
            byte[] out=new byte[4096];
            int n=enc.encode(samples,0,1440,out,0,out.length);
            if(n<1)throw new IOException("Falha na codificação Opus");
            if(!sink.send(Arrays.copyOf(out,n)))break;
            sent++;
            // Duas tramas de pré-buffer; depois ritmo nominal, sem ocupar a UI.
            long deadline=start+Math.max(0,i-1)*60L;
            long pause=deadline-android.os.SystemClock.elapsedRealtime();
            if(pause>0)android.os.SystemClock.sleep(Math.min(120,pause));
        }
        return sent;
    }
    static byte[] wav(byte[] rawPcm) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(rawPcm.length+44);
        ascii(b,"RIFF");le32(b,rawPcm.length+36);ascii(b,"WAVEfmt ");
        le32(b,16);le16(b,1);le16(b,1);le32(b,16000);
        le32(b,32000);le16(b,2);le16(b,16);
        ascii(b,"data");le32(b,rawPcm.length);b.write(rawPcm);
        return b.toByteArray();
    }
    static void appendShorts(ByteArrayOutputStream target,short[] values){
        for(short s:values){target.write(s&255);target.write((s>>8)&255);}
    }
    private static void ascii(ByteArrayOutputStream b,String s)throws IOException{
        b.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private static void le16(ByteArrayOutputStream b,int v){b.write(v&255);b.write((v>>8)&255);}
    private static void le32(ByteArrayOutputStream b,int v){
        le16(b,v);le16(b,v>>16);
    }
}