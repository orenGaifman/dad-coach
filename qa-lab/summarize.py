import sys,re,json
def comp(m):
    try: d=json.loads(m.group(2))
    except Exception: return "📊 "+m.group(1)+" (truncated json)"
    h=d.get('/father/home',{})
    if not isinstance(h,dict): return "📊 "+m.group(1)+": "+str(h)[:300]
    ss=[(s['localDate'][5:],s['localStart'],s['durationMinutes'],s['childName'],s['phase']) for s in h.get('sessionsThisWeek',[])]
    ch=[(c.get('name'),c.get('age')) for c in d.get('/father/children',[]) ] if isinstance(d.get('/father/children'),list) else d.get('/father/children')
    g=h.get('goal') or {}
    c=h.get('coverage') or {}
    return f"📊 {m.group(1)}: name={h.get('name')} goal={g.get('targetHours')}h/{g.get('status')} done={c.get('completedMinutes')} planned={c.get('plannedMinutes')} next={(h.get('nextSession') or {}).get('localDate')} {(h.get('nextSession') or {}).get('localStart')} sessions={ss} await={len(h.get('awaitingConfirmation',[]))} belt={(h.get('progress') or {}).get('belt')}/{(h.get('progress') or {}).get('totalCompleted')} children={ch}"
t=open(sys.argv[1]).read()
t=re.sub(r'<details><summary>📊 dashboard (.*?)</summary>\s*```json\n(.*?)\n```\n</details>',comp,t,flags=re.S)
t=t.replace("      ❤️ Dad Coach:\n","")
print(t)
