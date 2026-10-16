#!/usr/bin/env python3
"""Deterministic Block 50 fantasy-only, role-aware value model."""
import csv, hashlib, json, math, statistics
from collections import defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]; IN=ROOT/'data/statistics/features/player-performance-import.csv'; OUT=ROOT/'data/statistics/value-model'
VERSION='B50_FANTASY_VALUE_V1'; COMPONENTS=['fantasy_quality','usage','production','consistency','discipline','goalkeeper_specific']
WEIGHTS={'P':{'fantasy_quality':45,'usage':30,'goalkeeper_specific':20,'consistency':5},'D':{'fantasy_quality':40,'usage':30,'production':15,'consistency':10,'discipline':5},'C':{'fantasy_quality':35,'production':30,'usage':20,'consistency':10,'discipline':5},'A':{'production':40,'fantasy_quality':35,'usage':15,'consistency':7,'discipline':3}}

def read(p):
 with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def write(name,rows,fields=None):
 OUT.mkdir(parents=True,exist_ok=True); fields=fields or list(rows[0]);
 with (OUT/name).open('w',encoding='utf-8',newline='') as f:w=csv.DictWriter(f,fieldnames=fields,quoting=csv.QUOTE_ALL);w.writeheader();w.writerows(rows)
def pct(items,invert=False):
 groups=defaultdict(list)
 for k,v in items:
  if v is not None:groups[v].append(k)
 n=sum(map(len,groups.values())); result={}; cursor=0
 for v in sorted(groups):
  score=50 if n==1 else 100*(cursor+(len(groups[v])-1)/2)/(n-1)
  for k in groups[v]:result[k]=round(100-score if invert else score,2)
  cursor+=len(groups[v])
 return result
def num(v): return None if v is None or v=='' else float(v)
def mean(vals,weights=None):
 pairs=[(v,1 if weights is None else weights[i]) for i,v in enumerate(vals) if v is not None]
 return None if not pairs else sum(v*w for v,w in pairs)/sum(w for v,w in pairs)
def q(vals,p):
 vals=sorted(vals); x=(len(vals)-1)*p; lo=math.floor(x); hi=math.ceil(x); return vals[lo] if lo==hi else vals[lo]+(vals[hi]-vals[lo])*(x-lo)
def fmt(v,n=2): return '' if v is None else f'{v:.{n}f}'

def build():
 rows=read(IN)
 if len(rows)!=495: raise RuntimeError('B49 input must contain 495 rows')
 for r in rows:r['f']=json.loads(r['feature_values_json'])
 byrole=defaultdict(list)
 for r in rows:byrole[r['current_role']].append(r)
 avgpct={}; rawgp={}; rawap={}; dis={}; gkp={}; trendp={}; deltap={}
 for role,rs in byrole.items():
  avgpct.update(pct([(r['player_id'],num(r['f'].get('average_rating_latest') or r['f'].get('average_rating_previous'))) for r in rs]))
  fanta=[r for r in rs if r['f'].get('latest_source')=='FANTACALCIO']
  rawgp.update(pct([(r['player_id'],num(r['f'].get('goals_latest'))) for r in fanta])); rawap.update(pct([(r['player_id'],num(r['f'].get('assists_latest'))) for r in fanta]))
  tm=[r for r in rs if r['f'].get('latest_source')=='TRANSFERMARKT' and not (r['small_sample_flag']=='YES')]
  dis.update(pct([(r['player_id'],num(r['f'].get('discipline_events_per_90_latest'))) for r in tm],True))
  keep=[r for r in rs if r['f'].get('fantacalcio_voted_appearances_latest') not in (None,0)]
  gkp.update(pct([(r['player_id'],num(r['f'].get('penalties_saved_latest'))) for r in keep]))
  gc=[(r['player_id'],num(r['f'].get('goals_conceded_latest'))/num(r['f'].get('fantacalcio_voted_appearances_latest'))) for r in keep if num(r['f'].get('goals_conceded_latest')) is not None]
  gc_inv=pct(gc,True); gkp.update({k:round(mean([gkp.get(k),v],[.6,.4]),2) for k,v in gc_inv.items()})
  comparable=[r for r in rs if r['history_seasons_available']=='2' and r['source_mix']!='MIXED']
  stability=[]
  for r in comparable:
   f=r['f']; ds=[]
   if f.get('fantasy_average_trend') is not None:ds.append(abs(float(f['fantasy_average_trend'])))
   if f.get('delta_goals_per_90') is not None:ds.append(abs(float(f['delta_goals_per_90'])))
   if f.get('delta_assists_per_90') is not None:ds.append(abs(float(f['delta_assists_per_90'])))
   if ds:stability.append((r['player_id'],sum(ds)/len(ds)))
  deltap.update(pct(stability,True))
 profiles=[]; explanations=[]
 for r in rows:
  f=r['f']; pid=r['player_id']; role=r['current_role']; fp=num(f.get('fantasy_average_role_percentile'))
  fq=mean([fp,avgpct.get(pid)],[.8,.2])
  prod=mean([num(f.get('goal_rate_role_percentile')) if role!='P' else None,num(f.get('assist_rate_role_percentile')) if role!='P' else None,rawgp.get(pid) if role!='P' else None,rawap.get(pid) if role!='P' else None])
  scores={'fantasy_quality':fq,'usage':num(f.get('usage_role_percentile')),'production':prod,'consistency':deltap.get(pid),'discipline':dis.get(pid),'goalkeeper_specific':gkp.get(pid) if role=='P' else None}
  available=[c for c in WEIGHTS[role] if scores[c] is not None]; ranked=len(available)>=2
  total=sum(WEIGHTS[role][c] for c in available); effective={c:WEIGHTS[role][c]/total for c in available}
  value=sum(scores[c]*effective[c] for c in available) if ranked else None
  payload={'base_weights':WEIGHTS[role],'effective_weights':{c:round(w,6) for c,w in effective.items()},'available_components':available,'minimum_components':2}
  fingerprint=hashlib.sha256((VERSION+'|'+r['input_fingerprint']+'|'+json.dumps(payload,sort_keys=True,separators=(',',':'))+'|'+json.dumps(scores,sort_keys=True,separators=(',',':'))).encode()).hexdigest()
  p={'player_id':pid,'player_name':r['player_name'],'role':role,'current_club_id':r['current_club_id'],'club':r['current_club'],'reference_season':r['reference_season'],'source_mix':r['source_mix'],'history_seasons_available':r['history_seasons_available'],'small_sample_flag':r['small_sample_flag'],'data_confidence_score':r['data_confidence_score'],**{c+'_score':scores[c] for c in COMPONENTS},'player_value_score':value,'role_rank':None,'role_percentile':None,'ranking_status':'RANKED' if ranked else 'INSUFFICIENT_EVIDENCE','algorithm_version':VERSION,'component_payload_json':json.dumps(payload,sort_keys=True,separators=(',',':')),'evidence_json':json.dumps({'b49_algorithm':'B49_FEATURES_V1','b49_input_fingerprint':r['input_fingerprint'],'confidence_separate':True},sort_keys=True,separators=(',',':')),'input_fingerprint':fingerprint}
  profiles.append(p)
  for c in available: explanations.append({'player_id':pid,'component':c,'raw_input_summary':'B49 certified inputs','normalized_input':fmt(scores[c]),'weight':fmt(effective[c]*100),'weighted_contribution':fmt(scores[c]*effective[c],4),'reason':'role-aware available component; NULL weights renormalized'})
 for role in 'PDCA':
  ranked=[p for p in profiles if p['role']==role and p['player_value_score'] is not None]; ranked.sort(key=lambda p:(-p['player_value_score'],int(p['player_id'])))
  valuepct=pct([(p['player_id'],p['player_value_score']) for p in ranked]); last=None; rank=0
  for i,p in enumerate(ranked,1):
   if last is None or round(p['player_value_score'],10)!=round(last,10):rank=i
   p['role_rank']=rank;p['role_percentile']=valuepct[p['player_id']];last=p['player_value_score']
 fields=['player_id','player_name','role','current_club_id','club','reference_season','source_mix','history_seasons_available','small_sample_flag','data_confidence_score']+[c+'_score' for c in COMPONENTS]+['player_value_score','role_rank','role_percentile','ranking_status','algorithm_version','component_payload_json','evidence_json','input_fingerprint']
 export=[]
 for p in profiles:export.append({k:(fmt(v) if isinstance(v,float) else ('' if v is None else v)) for k,v in p.items()})
 write('player-fantasy-value-ranking.csv',export,fields); write('player-fantasy-value-import.csv',export,fields); write('player-fantasy-value-explainability.csv',explanations)
 definitions=[]
 for role in 'PDCA':
  for c,w in WEIGHTS[role].items():definitions.append({'role':role,'component':c,'weight':w,'formula':'weighted mean of certified role percentiles','inputs':'B49_FEATURES_V1','null_policy':'NULL stays NULL; available weights renormalized','eligibility':'certified semantic input available','version':VERSION})
 write('fantasy-value-model-definition.csv',definitions)
 audit_artifacts(profiles)
 return profiles

def audit_artifacts(ps):
 cov=[]; dist=[]
 for role in 'PDCA':
  rs=[p for p in ps if p['role']==role]
  for c in COMPONENTS+['player_value']:
   vals=[p[c+'_score'] for p in rs if p[c+'_score'] is not None]
   cov.append({'role':role,'component':c,'available':len(vals),'missing':len(rs)-len(vals),'coverage_pct':fmt(100*len(vals)/len(rs))})
   dist.append({'role':role,'component':c,'min':fmt(min(vals)) if vals else '','p10':fmt(q(vals,.1)) if vals else '','p25':fmt(q(vals,.25)) if vals else '','median':fmt(q(vals,.5)) if vals else '','p75':fmt(q(vals,.75)) if vals else '','p90':fmt(q(vals,.9)) if vals else '','max':fmt(max(vals)) if vals else '','null_count':len(rs)-len(vals)})
 write('component-coverage-by-role.csv',cov);write('component-distribution-by-role.csv',dist)
 source=[]
 for role in 'PDCA':
  for mix in ('TWO_FANTACALCIO','MIXED','TWO_INTERNATIONAL','ONE_SEASON_ONLY'):
   vals=[p['player_value_score'] for p in ps if p['role']==role and p['source_mix']==mix and p['player_value_score'] is not None]
   source.append({'role':role,'source_mix':mix,'count':len(vals),'mean':fmt(statistics.mean(vals)) if vals else '','median':fmt(statistics.median(vals)) if vals else '','min':fmt(min(vals)) if vals else '','max':fmt(max(vals)) if vals else ''})
 write('source-mix-score-audit.csv',source)
 corr=[]
 for role in 'PDCA':
  for i,a in enumerate(COMPONENTS):
   for b in COMPONENTS[i+1:]:
    pairs=[(p[a+'_score'],p[b+'_score']) for p in ps if p['role']==role and p[a+'_score'] is not None and p[b+'_score'] is not None]
    pear=''
    if len(pairs)>2 and statistics.pstdev([x for x,y in pairs]) and statistics.pstdev([y for x,y in pairs]):pear=fmt(statistics.correlation([x for x,y in pairs],[y for x,y in pairs]),4)
    corr.append({'role':role,'component_a':a,'component_b':b,'pair_count':len(pairs),'pearson':pear})
 write('component-correlation-audit.csv',corr)
 anomalies=[]
 for p in ps:
  flags=[]
  if p['player_value_score'] is None:flags.append('NULL_FINAL_SCORE')
  if p['small_sample_flag']=='YES':flags.append('SMALL_SAMPLE')
  if p['player_value_score'] is not None and p['player_value_score']>=80 and float(p['data_confidence_score'])<75:flags.append('HIGH_SCORE_LOW_CONFIDENCE')
  vals=[p[c+'_score'] for c in COMPONENTS if p[c+'_score'] is not None]
  if vals and max(vals)-min(vals)>=70:flags.append('EXTREME_COMPONENT_DISAGREEMENT')
  for flag in flags:anomalies.append({'player_id':p['player_id'],'player_name':p['player_name'],'role':p['role'],'anomaly':flag,'value_score':fmt(p['player_value_score']),'confidence':p['data_confidence_score']})
 write('player-value-anomalies.csv',anomalies or [{'player_id':'','player_name':'','role':'','anomaly':'NONE','value_score':'','confidence':''}])
 tb=[]
 for role in 'PDCA':
  rs=sorted([p for p in ps if p['role']==role and p['player_value_score'] is not None],key=lambda p:p['role_rank']); chosen=rs[:10]+rs[-10:]
  for p in chosen:tb.append({'role':role,'position':p['role_rank'],'player_id':p['player_id'],'player_name':p['player_name'],'player_value_score':fmt(p['player_value_score']),'confidence':p['data_confidence_score'],'source_mix':p['source_mix'],'reason_summary':'deterministic role-aware weighted components'})
 write('top-bottom-role-ranking-audit.csv',tb)
 golden=[]
 for role in 'PDCA':
  rs=sorted([p for p in ps if p['role']==role],key=lambda p:(p['role_rank'] or 9999));
  for p in (rs[:1]+rs[-1:]):golden.append({'player_id':p['player_id'],'player_name':p['player_name'],'role':role,'source_mix':p['source_mix'],'small_sample':p['small_sample_flag'],'confidence':p['data_confidence_score'],'score':fmt(p['player_value_score']),'rank':p['role_rank'],'selection_reason':'role top/bottom audit'})
 write('golden-player-value-audit.csv',golden)

if __name__=='__main__': build(); print('B50 artifacts: PASS')
