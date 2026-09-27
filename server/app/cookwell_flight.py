import re, json

def parse(s: str) -> dict:
    """Parse a Next.js RSC flight payload into {id: value}."""
    b = s.encode()
    out, i, n = {}, 0, len(b)
    while i < n:
        j = b.index(b':', i)
        rid = b[i:j].decode().strip()
        i = j + 1
        if b[i:i+1] == b'T':
            k = b.index(b',', i)
            ln = int(b[i+1:k], 16)
            out[rid] = b[k+1:k+1+ln].decode()
            i = k + 1 + ln
        else:
            k = b.find(b'\n', i)
            k = n if k < 0 else k
            raw = b[i:k].decode()
            try:
                out[rid] = json.loads(raw)
            except ValueError:
                out[rid] = raw
            i = k + 1
    return out

def resolve(recs, v, depth=0):
    if depth > 60:
        return None
    if isinstance(v, str) and v.startswith('$') and len(v) > 1:
        ref = v[1:]
        if ref == 'undefined':
            return None
        if re.fullmatch(r'[0-9a-f]+', ref) and ref in recs:
            return resolve(recs, recs[ref], depth + 1)
        return v
    if isinstance(v, list):
        return [resolve(recs, x, depth + 1) for x in v]
    if isinstance(v, dict):
        return {k: resolve(recs, x, depth + 1) for k, x in v.items()}
    return v
