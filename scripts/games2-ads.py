#!/usr/bin/env python3
"""ADS-Ebene von AMP: Instanzen listen und starten (geht auch, wenn die Instanz aus ist)."""
import json, os, sys, urllib.request

CRED = os.path.expanduser("~/.config/amp/credentials")
c = {}
for line in open(CRED):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        k, v = line.split("=", 1)
        c[k.strip()] = v.strip()
BASE = c["AMP_URL"].rstrip("/")

def call(method, sid=None, **payload):
    if sid:
        payload["SESSIONID"] = sid
    req = urllib.request.Request(BASE + "/API/" + method, data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json", "Accept": "text/javascript"})
    with urllib.request.urlopen(req, timeout=60) as r:
        body = r.read()
    return json.loads(body) if body.strip() else None

r = call("Core/Login", username=c["AMP_USERNAME"], password=c["AMP_PASSWORD"], token="", rememberMe=False)
if not r.get("success"):
    sys.exit("!! ADS-Login fehlgeschlagen: " + str(r.get("resultReason") or r.get("result")))
sid = r["sessionID"]

cmd = sys.argv[1] if len(sys.argv) > 1 else "list"
if cmd == "list":
    res = call("ADSModule/GetInstances", sid)
    for target in (res if isinstance(res, list) else res.get("result", [])):
        for inst in target.get("AvailableInstances", []):
            print(f"{inst.get('InstanceID')}  {inst.get('InstanceName'):28s} "
                  f"running={inst.get('Running')}  state={inst.get('AppState')}")
elif cmd == "start":
    print(json.dumps(call("ADSModule/StartInstance", sid, InstanceName=sys.argv[2]), indent=1)[:600])
elif cmd == "stop":
    print(json.dumps(call("ADSModule/StopInstance", sid, InstanceName=sys.argv[2]), indent=1)[:600])
