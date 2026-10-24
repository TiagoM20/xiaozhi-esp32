"""LUMI Wake Monitor: read-only ESP32 event diagnostics. Never flashes or erases anything."""
import json
import queue
import re
import sys
import threading
import time
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import ttk, messagebox
from serial.tools import list_ports
import serial

BAUD=115200
WAKE_RE=re.compile(r"Custom wake word detected:\s*command_id=(\d+),\s*string=.*?,\s*prob=([0-9.]+)",re.I)
STATE_RE=re.compile(r"State:\s*([a-z_]+)\s*->\s*([a-z_]+)",re.I)
APP_WAKE_RE=re.compile(r"Wake word detected:.*?\(state:\s*(\d+)\)",re.I)
THRESH_RE=re.compile(r"LUMI Quiet:\s*wake threshold\s*([0-9.]+)")
EVT_RE=re.compile(r"LUMI Quiet:\s*(.*)",re.I)
MAX_EVENTS=250
DURATION=600

def recognize(line):
    """Deliberate allowlist. Never save raw device logs, transcripts, credentials, IPs."""
    k=WAKE_RE.search(line)
    if k:return ("kws",f"MultiNet detetou comando {k.group(1)} (probabilidade {k.group(2)})",float(k.group(2)))
    k=STATE_RE.search(line)
    if k:return ("state",f"{k.group(1)} → {k.group(2)}",(k.group(1),k.group(2)))
    k=APP_WAKE_RE.search(line)
    if k:return ("wake",f"Evento de despertar recebido (estado {k.group(1)})",None)
    k=THRESH_RE.search(line)
    if k:return ("threshold",f"Limiar ativo: {k.group(1)}",float(k.group(1)))
    k=EVT_RE.search(line)
    if k:
        what=k.group(1).lower()
        if "ignoring wake event" in what or "wake ignored" in what: return ("guard","Despertar rejeitado pela proteção",None)
        if "silent listening timeout" in what:return ("timeout","Escuta sem pergunta terminou",None)
        if "suppressing automatic greeting" in what:return ("guard","Saudação automática bloqueada",None)
        if "user stt confirmed" in what:return ("stt","Pergunta reconhecida (conteúdo omitido)",None)
    return None

class Monitor:
    def __init__(self,root):
        self.root=root
        root.title("LUMI | Diagnóstico de Despertares")
        root.geometry("820x660")
        root.configure(bg="#0B1527")
        self.q=queue.Queue(); self.stop=threading.Event();self.worker=None
        self.events=[];self.detects=0;self.entered=0;self.unsolicited=0;self.manual=0
        self.last_kws=0.; self.last_prob=None;self.start_time=None;self.portvar=tk.StringVar()
        tk.Label(root,text="⌂  LUMI  |  DESPERTARES",font=("Segoe UI Semibold",19),fg="#FFFFFF",bg="#0B1527").pack(anchor="w",padx=24,pady=(18,0))
        tk.Label(root,text="Observação da DESK em tempo real  •  só leitura  •  sem firmware ou gravação",font=("Segoe UI",10),fg="#C7D7ED",bg="#0B1527").pack(anchor="w",padx=25,pady=(0,12))
        tk.Label(root,text="Deixa a LUMI em repouso, sem dizer «Olá Lumi», durante 5–10 minutos.",font=("Segoe UI Semibold",10),fg="#FFC107",bg="#0B1527").pack(anchor="w",padx=25)
        tools=tk.Frame(root,bg="#172841");tools.pack(fill="x",padx=24,pady=14)
        tk.Label(tools,text="Porta USB:",fg="white",bg="#172841").pack(side="left",padx=(14,5),pady=14)
        self.combo=ttk.Combobox(tools,textvariable=self.portvar,width=12,state="readonly")
        self.combo.pack(side="left")
        self.refreshbtn=tk.Button(tools,text="Atualizar",command=self.refresh,bg="#334A70",fg="white",relief="flat",padx=10)
        self.refreshbtn.pack(side="left",padx=8)
        self.startbtn=tk.Button(tools,text="INICIAR",command=self.begin,bg="#0668E7",fg="white",relief="flat",padx=16,pady=7,font=("Segoe UI Semibold",10))
        self.startbtn.pack(side="left",padx=6)
        self.stopbtn=tk.Button(tools,text="PARAR / GUARDAR",command=self.finish,bg="#395271",fg="white",relief="flat",padx=12,pady=7,state="disabled")
        self.stopbtn.pack(side="right",padx=13)
        self.info=tk.StringVar(value="Pronto. Liga a LUMI por USB, fecha o Gestor anterior e inicia.")
        tk.Label(root,textvariable=self.info,fg="#B6CDE8",bg="#0B1527",font=("Segoe UI",10),anchor="w").pack(fill="x",padx=24)
        self.stats=tk.StringVar(value="Despertares MultiNet: 0   |   Entradas em escuta: 0   |   Sem deteção próxima: 0")
        tk.Label(root,textvariable=self.stats,fg="white",bg="#0B1527",font=("Segoe UI Semibold",11),anchor="w").pack(fill="x",padx=24,pady=15)
        box=tk.Frame(root,bg="#122239");box.pack(fill="both",expand=True,padx=24,pady=(0,18))
        self.text=tk.Text(box,bg="#122239",fg="#DAEBFF",relief="flat",font=("Consolas",10),wrap="word",padx=11,pady=11)
        self.text.pack(fill="both",expand=True);self.text.configure(state="disabled")
        tk.Label(root,text="O relatório exclui conversas, palavras reconhecidas, rede e credenciais.",bg="#0B1527",fg="#A5BAD2",font=("Segoe UI",9)).pack(anchor="w",padx=24,pady=(0,14))
        self.refresh(); root.after(100,self.drain)

    def log(self,msg):
        self.text.configure(state="normal")
        self.text.insert("end",msg+"\n");self.text.see("end")
        self.text.configure(state="disabled")
    def refresh(self):
        names=[p.device for p in list_ports.comports()]
        self.combo["values"]=names
        if names and self.portvar.get() not in names:self.portvar.set("COM5" if "COM5" in names else names[0])
        if not names:self.portvar.set("")
    def begin(self):
        if self.worker and self.worker.is_alive():return
        port=self.portvar.get()
        if not port:messagebox.showerror("USB","Porta COM não encontrada.");return
        self.stop.clear();self.events=[];self.detects=0;self.entered=0;self.unsolicited=0;self.manual=0;self.last_kws=0.;self.last_prob=None
        self.start_time=time.monotonic()
        self.stats.set("Despertares MultiNet: 0   |   Entradas em escuta: 0   |   Sem deteção próxima: 0")
        self.startbtn.configure(state="disabled");self.stopbtn.configure(state="normal");self.combo.configure(state="disabled")
        self.log("--- Observação iniciada (10 minutos, sem enviar comandos à DESK) ---")
        self.info.set("A monitorizar em "+port+" — deixa a DESK em repouso sem lhe falar.")
        self.worker=threading.Thread(target=self.read_serial,args=(port,),daemon=True);self.worker.start()
    def read_serial(self,port):
        try:
            # Configure DTR/RTS before opening to reduce USB bridge resets.
            link=serial.Serial(port=None,baudrate=BAUD,timeout=.25)
            link.dtr=False;link.rts=False;link.port=port;link.open()
            try:
                while not self.stop.is_set() and time.monotonic()-self.start_time<DURATION:
                    raw=link.readline()
                    if not raw:continue
                    line=raw.decode("utf-8","replace").strip()
                    info=recognize(line)
                    if info:self.q.put(("event",info))
            finally:link.close()
            self.q.put(("finished",None))
        except Exception as ex:
            self.q.put(("error",str(ex)))
    def apply_event(self,rec):
        kind,msg,data=rec;now=time.monotonic();stamp=datetime.now().strftime("%H:%M:%S")
        if kind=="kws":
            self.detects+=1;self.last_kws=now;self.last_prob=data
        if kind=="state" and data[1]=="listening":
            self.entered+=1
            # The transition can happen 0–5 sec after keyword due to WLAN latency.
            if (now-self.last_kws)>5:
                self.unsolicited+=1
                msg+="  [SEM evento MultiNet nos 5s anteriores]"
            else:
                msg+=f"  [MultiNet anterior {self.last_prob:.2f}]"
        if kind=="state" and data[0]=="idle" and data[1] in ("listening","connecting"):
            # A click/touch might skip the keyword callback.
            if now-self.last_kws>5 and data[1]=="connecting":self.manual+=1
        item={"hora":stamp,"tipo":kind,"descricao":msg}
        if len(self.events)<MAX_EVENTS:self.events.append(item)
        self.log(f"{stamp}  {msg}")
        self.stats.set(f"Deteções MultiNet: {self.detects}   |   Entradas em escuta: {self.entered}   |   Sem MultiNet próximo: {self.unsolicited}")
    def drain(self):
        while True:
            try:kind,val=self.q.get_nowait()
            except queue.Empty:break
            if kind=="event":self.apply_event(val)
            elif kind=="error":
                self.stop.set();self.log("ERRO NA LIGAÇÃO: "+val)
                self.info.set("Falha de porta. Fecha outras aplicações que estejam a usar a COM.")
                self.startbtn.configure(state="normal");self.stopbtn.configure(state="disabled");self.combo.configure(state="readonly")
            elif kind=="finished":
                if self.start_time is not None:self.finish()
        self.root.after(100,self.drain)
    def report(self):
        folder=Path.home()/"Documents"/"LUMI-DESK"/"Diagnosticos"
        folder.mkdir(parents=True,exist_ok=True)
        file=folder/("LUMI-despertares-"+datetime.now().strftime("%Y%m%d-%H%M%S")+".json")
        payload={"monitor":"LUMI Wake Monitor 0.1.0","duracao_segundos":round(time.monotonic()-(self.start_time or time.monotonic())),
          "deteccoes_multinet":self.detects,"entradas_escuta":self.entered,"entradas_sem_multinet_proximo":self.unsolicited,
          "eventos_sem_dados_privados":self.events,
          "limites":"Apenas eventos a partir de abertura da COM. Sem detecao anterior nao prova botao; pode haver logs perdidos."}
        file.write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding="utf-8")
        self.log("RELATÓRIO: "+str(file))
        return file
    def finish(self):
        if self.start_time is None:return
        self.stop.set()
        file=self.report()
        self.start_time=None
        self.startbtn.configure(state="normal");self.stopbtn.configure(state="disabled");self.combo.configure(state="readonly")
        self.info.set(f"Diagnóstico terminado. Relatório: {file.name}")
        messagebox.showinfo("Diagnóstico guardado","Relatório sem transcrições guardado em:\n"+str(file))
def selftest():
    samples=["I (12) CustomWakeWord: Custom wake word detected: command_id=1, string=ola lumi, prob=0.360000",
             "I StateMachine: State: idle -> connecting","I StateMachine: State: connecting -> listening",
             "I Application: Wake word detected: Olá Lumi (state: 3)",
             "I Application: LUMI Quiet: silent listening timeout (user=0)",
             "I Application: >> texto privado"]
    assert recognize(samples[0])[0]=="kws"
    assert recognize(samples[1])[0]=="state"
    assert recognize(samples[2])[0]=="state"
    assert recognize(samples[3])[0]=="wake"
    assert recognize(samples[4])[0]=="timeout"
    assert recognize(samples[5]) is None
    return {"selftest":True,"serial":"pyserial","mode":"read-only","private_transcripts_saved":False}
if __name__=="__main__":
    if len(sys.argv)>2 and sys.argv[1]=="--self-test":
        Path(sys.argv[2]).write_text(json.dumps(selftest()),encoding="utf-8")
    else:
        app=tk.Tk();Monitor(app);app.mainloop()
