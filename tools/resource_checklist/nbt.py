import gzip,struct,os,sys,json,collections
def rd(b,i,t):
    if t==1: return b[i],i+1
    if t==2: return struct.unpack_from(">h",b,i)[0],i+2
    if t==3: return struct.unpack_from(">i",b,i)[0],i+4
    if t==4: return struct.unpack_from(">q",b,i)[0],i+8
    if t==5: return struct.unpack_from(">f",b,i)[0],i+4
    if t==6: return struct.unpack_from(">d",b,i)[0],i+8
    if t==7:
        n=struct.unpack_from(">i",b,i)[0]; return None,i+4+n
    if t==8:
        n=struct.unpack_from(">H",b,i)[0]; i+=2
        return b[i:i+n].decode("utf-8","replace"),i+n
    if t==9:
        et=b[i]; n=struct.unpack_from(">i",b,i+1)[0]; i+=5
        out=[]
        for _ in range(n):
            v,i=rd(b,i,et); out.append(v)
        return out,i
    if t==10:
        out={}
        while True:
            tt=b[i]; i+=1
            if tt==0: return out,i
            n=struct.unpack_from(">H",b,i)[0]; i+=2
            k=b[i:i+n].decode("utf-8","replace"); i+=n
            v,i=rd(b,i,tt); out[k]=v
    if t==11:
        n=struct.unpack_from(">i",b,i)[0]; return None,i+4+n*4
    if t==12:
        n=struct.unpack_from(">i",b,i)[0]; return None,i+4+n*8
    raise ValueError("tag "+str(t))
def parse(p):
    raw=open(p,"rb").read()
    if raw[:2]==b"\x1f\x8b": raw=gzip.decompress(raw)
    i=0; t=raw[i]; i+=1
    n=struct.unpack_from(">H",raw,i)[0]; i+=2+n
    v,_=rd(raw,i,t); return v
from paths import MCDATA, WORK
ROOT=os.path.join(MCDATA,"data","minecraft","structure")
sb=collections.defaultdict(set); errs=0
for dp,_,fns in os.walk(ROOT):
    for fn in fns:
        if not fn.endswith(".nbt"): continue
        p=os.path.join(dp,fn)
        rel=os.path.relpath(p,ROOT).replace("\\","/")
        top=rel.split("/")[0]
        try: j=parse(p)
        except Exception: errs+=1; continue
        pals=[]
        if "palette" in j: pals.append(j["palette"])
        for pp in j.get("palettes",[]) or []: pals.append(pp)
        for pal in pals:
            for e in pal or []:
                if isinstance(e,dict) and isinstance(e.get("Name"),str): sb[top].add(e["Name"])
json.dump({k:sorted(v) for k,v in sb.items()},open(os.path.join(WORK,"structblocks.json"),"w"),indent=0)
print("errors:",errs)
for k in sorted(sb): print(f"{k:18} {len(sb[k]):4} distinct blocks")
print("union:",len(set().union(*sb.values())))
