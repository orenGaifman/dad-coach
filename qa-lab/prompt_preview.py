"""Saves the raw prompt the model receives for every state of dad-coach-3 (platform prompt-preview) into
.run/preview/<tag>/<STATE>.txt - diff two tags to prove a provisioning change moved only what it meant to."""
import os, sys
import qa

tag = sys.argv[1]
_, wfs = qa.platform("GET", "/api/v1/admin/workflows?size=50")
wf = next(w for w in wfs["workflows"] if w["workflowKey"] == "dad-coach-3")
_, states = qa.platform("GET", f"/api/v1/admin/workflows/{wf['id']}/states")
states = states.get("content", states) if isinstance(states, dict) else states
os.makedirs(f"{qa.LAB}/.run/preview/{tag}", exist_ok=True)
for s in states:
    _, p = qa.platform("GET", f"/api/v1/admin/workflows/{wf['id']}/states/{s['id']}/prompt-preview")
    text = p.get("fullPrompt") or p.get("prompt") or p.get("systemPrompt") if isinstance(p, dict) else str(p)
    if not text and isinstance(p, dict):
        text = "\n".join(f"{k}: {v}" for k, v in p.items())
    open(f"{qa.LAB}/.run/preview/{tag}/{s['stateKey']}.txt", "w").write(text or "")
    print(s["stateKey"], len(text or ""))
