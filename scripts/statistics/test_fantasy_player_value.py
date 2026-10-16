import csv,json,subprocess,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; FILE=ROOT/'data/statistics/value-model/player-fantasy-value-ranking.csv'
class FantasyValueTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  subprocess.run([sys.executable,str(ROOT/'scripts/statistics/build_fantasy_player_value.py')],check=True,capture_output=True)
  with FILE.open(encoding='utf-8',newline='') as f:cls.rows=list(csv.DictReader(f))
 def test_495_and_version(self): self.assertEqual(495,len(self.rows));self.assertEqual({'B50_FANTASY_VALUE_V1'},{r['algorithm_version'] for r in self.rows})
 def test_roles(self): self.assertEqual({'P':58,'D':176,'C':174,'A':87},{x:sum(r['role']==x for r in self.rows) for x in 'PDCA'})
 def test_bounds_and_null(self):
  for r in self.rows:
   for k in ('fantasy_quality_score','usage_score','production_score','consistency_score','discipline_score','goalkeeper_specific_score','player_value_score','role_percentile'):
    if r[k]!='':self.assertTrue(0<=float(r[k])<=100,(r['player_id'],k,r[k]))
 def test_confidence_separate(self):
  for r in self.rows:self.assertTrue(json.loads(r['evidence_json'])['confidence_separate'])
 def test_goalkeeper_branch(self):
  self.assertTrue(all(r['production_score']=='' for r in self.rows if r['role']=='P'))
  self.assertTrue(all(r['goalkeeper_specific_score']=='' for r in self.rows if r['role']!='P'))
 def test_weight_renormalization(self):
  for r in self.rows:
   p=json.loads(r['component_payload_json'])
   if p['available_components']:self.assertAlmostEqual(1,sum(p['effective_weights'].values()),5)
 def test_minimum_evidence(self):
  for r in self.rows:
   p=json.loads(r['component_payload_json']);self.assertEqual(r['ranking_status']=='RANKED',len(p['available_components'])>=2)
 def test_rank_deterministic_and_role_local(self):
  for role in 'PDCA':
   rs=[r for r in self.rows if r['role']==role and r['role_rank']!=''];self.assertEqual(1,min(int(r['role_rank']) for r in rs));self.assertEqual(len(rs),len({r['player_id'] for r in rs}))
 def test_one_season_consistency_null(self): self.assertTrue(all(r['consistency_score']=='' for r in self.rows if r['source_mix']=='ONE_SEASON_ONLY'))
 def test_explainability_sums(self):
  with (ROOT/'data/statistics/value-model/player-fantasy-value-explainability.csv').open(encoding='utf-8',newline='') as f: ex=list(csv.DictReader(f))
  sums={}
  for x in ex:sums[x['player_id']]=sums.get(x['player_id'],0)+float(x['weighted_contribution'])
  for r in self.rows:
   if r['player_value_score']!='':self.assertLessEqual(abs(float(r['player_value_score'])-sums[r['player_id']]),.011)
if __name__=='__main__':unittest.main()
