"""Native WakeNet assets repacker for LUMI DESK.

Matches XiaoZhi 2.5.3 scripts/build_default_assets.py mmap format.
No serial, network, or write-to-flash functions. Always preserve user image
assets byte-for-byte; replace only srmodels.bin and index.json.
"""
from collections import OrderedDict
import hashlib
import json
import struct

ASSETS_LIMIT = 0x800000
ENTRY = struct.Struct("<32sIIHH")
HEADER = struct.Struct("<III")
MODEL = "srmodels.bin"


def parse(blob, max_size=ASSETS_LIMIT):
    if len(blob) < HEADER.size or len(blob) > max_size:
        raise ValueError("Particao de assets truncada ou demasiado grande.")
    count, checksum, length = HEADER.unpack_from(blob)
    if not (1 <= count <= 4096) or count * ENTRY.size > length:
        raise ValueError("Cabecalho de assets nao reconhecido.")
    if length + HEADER.size > len(blob) or length > max_size - HEADER.size:
        raise ValueError("Dados de assets fora da particao.")
    payload = blob[HEADER.size:HEADER.size+length]
    if (sum(payload) & 0xffff) != checksum:
        raise ValueError("Checksum assets invalido.")
    result = OrderedDict()
    table = count * ENTRY.size
    for i in range(count):
        raw,size,offset,width,height = ENTRY.unpack_from(payload, i*ENTRY.size)
        name = raw.split(b"\0", 1)[0].decode("utf-8", "strict")
        beg = table + offset
        end = beg + size + 2
        if (not name or name in result or beg < table or end > len(payload)
                or payload[beg:beg+2] != b"ZZ"):
            raise ValueError("Entrada de assets invalida: " + str(name))
        result[name] = (payload[beg+2:end], width, height)
    if "index.json" not in result:
        raise ValueError("index.json nao encontrado.")
    index = json.loads(result["index.json"][0].decode("utf-8"))
    if not isinstance(index, dict) or index.get("version", 1) != 1:
        raise ValueError("Versao de assets nao suportada.")
    return result, index


def pack(files):
    table, body = bytearray(), bytearray()
    for name, (data,width,height) in files.items():
        enc = name.encode("utf-8")
        if len(enc) > 31 or not isinstance(data,bytes):
            raise ValueError("Nome de asset demasiado longo ou bytes invalidos.")
        table.extend(ENTRY.pack(enc, len(data), len(body), width, height))
        body.extend(b"ZZ"+data)
    payload = table+body
    output = HEADER.pack(len(files), sum(payload)&65535, len(payload)) + payload
    if len(output) > ASSETS_LIMIT:
        raise ValueError("WakeNet nativo e GIFs ultrapassam a particao 8 MB: PARAR.")
    return output


def merge(old_partition, native_template):
    old_files, old_index = parse(old_partition)
    new_files, new_index = parse(native_template)
    template_model_name = new_index.get("srmodels")
    if template_model_name != MODEL or MODEL not in new_files:
        raise ValueError("O pacote novo nao contem o modelo nativo WakeNet.")
    if "multinet_model" in new_index:
        raise ValueError("O pacote novo continua a ter MultiNet; bloqueado.")
    model = new_files[MODEL][0]
    if len(model) < 100000:
        raise ValueError("O modelo WakeNet esta incompleto.")
    old_model_name = old_index.get("srmodels", MODEL)
    if not isinstance(old_model_name,str) or old_model_name=="index.json":
        raise ValueError("Referencia ao modelo anterior invalida.")
    preserve = OrderedDict((name, value) for name,value in old_files.items()
                           if name not in (old_model_name, MODEL, "index.json"))
    original_preserved = {name:hashlib.sha256(val[0]).hexdigest()
                          for name,val in preserve.items()}
    old_index = dict(old_index)
    old_index.pop("multinet_model",None)
    old_index["srmodels"]=MODEL
    # Make index first, preserving the complete remaining manifest metadata.
    index_bytes=json.dumps(old_index,ensure_ascii=False,separators=(",",":")).encode("utf-8")
    merged = OrderedDict([("index.json",(index_bytes,0,0))])
    merged.update(preserve)
    merged[MODEL] = (model,0,0)
    output = pack(merged)
    verify,verify_index = parse(output)
    if verify_index != old_index:
        raise ValueError("Manifesto de assets alterado inesperadamente.")
    after = {name:hashlib.sha256(verify[name][0]).hexdigest()
             for name in preserve}
    if after != original_preserved:
        raise ValueError("Recursos visuais foram alterados: bloqueado.")
    if verify[MODEL][0] != model:
        raise ValueError("Modelo WakeNet nao passou na verificacao.")
    gifs=[name for name in preserve if name.lower().endswith(".gif")]
    if len(gifs)<21:
        raise ValueError("Esperados 21 GIFs no backup; foram encontrados "+str(len(gifs))+".")
    return output, {
        "gifs_preservados":len(gifs),
        "recursos_preservados":len(preserve),
        "tamanho_original":len(old_partition),
        "tamanho_novo":len(output),
        "modelo_native_bytes":len(model),
        "sha256_assets_novos":hashlib.sha256(output).hexdigest(),
        "sha256_21gifs":{n:original_preserved[n] for n in gifs[:21]},
    }
