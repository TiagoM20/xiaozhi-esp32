import json
import unittest
from collections import OrderedDict
import native_assets as n

def base():
    idx = {
        "version":1,
        "emoji_collection":[{"name":"happy","file":"happy.gif"}],
        "srmodels":"srmodels.bin",
        "multinet_model":{"language":"en","commands":[{"command":"ola lumi","text":"Olá Lumi","action":"wake"}]}
    }
    files=OrderedDict()
    files["index.json"]=(json.dumps(idx).encode(),0,0)
    for i in range(21):
        files[f"face{i}.gif"]=(bytes([i])+b"GIF89a"*20,128,128)
    files["noto-font.bin"]=(b"\x01\x02"*100,0,0)
    files["srmodels.bin"]=(b"x"*200000,0,0)
    return n.pack(files)

def native():
    idx={"version":1,"srmodels":"srmodels.bin"}
    f=OrderedDict([("index.json",(json.dumps(idx).encode(),0,0)),
                   ("srmodels.bin",(b"y"*300000,0,0))])
    return n.pack(f)

class PackTests(unittest.TestCase):
    def test_merge_preserves_21_faces(self):
        old=base()
        new,report=n.merge(old,native())
        before,oi=n.parse(old)
        after,ai=n.parse(new)
        self.assertEqual(report["gifs_preservados"],21)
        self.assertNotIn("multinet_model",ai)
        self.assertEqual(ai["srmodels"],"srmodels.bin")
        for k,v in before.items():
            if k not in ("index.json","srmodels.bin"):
                self.assertEqual(after[k],v)
        self.assertEqual(after["srmodels.bin"][0],b"y"*300000)
    def test_fails_invalid_checksum(self):
        b=bytearray(base());b[-30] ^= 0x10
        with self.assertRaises(ValueError):n.merge(bytes(b),native())
    def test_fails_too_few_gifs(self):
        b=base()
        files,index=n.parse(b)
        files.pop("face20.gif")
        with self.assertRaisesRegex(ValueError,"21 GIFs"):n.merge(n.pack(files),native())
    def test_fails_multinet_template(self):
        f,_=n.parse(native())
        f["index.json"]=(json.dumps({"version":1,"srmodels":"srmodels.bin","multinet_model":{}}).encode(),0,0)
        with self.assertRaisesRegex(ValueError,"MultiNet"):n.merge(base(),n.pack(f))

if __name__=="__main__":
    unittest.main()
