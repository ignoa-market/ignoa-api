"""Validate dashboard layout and PromQL syntax using promtool."""

import json
import subprocess
import tempfile
from pathlib import Path


dashboard = json.loads(
    (Path(__file__).resolve().parents[1] / "dashboards/ignoa-payment-trade.json").read_text()
)
panels = dashboard["panels"]
assert len({p["id"] for p in panels}) == len(panels), "Duplicate panel IDs"
for index, panel in enumerate(panels):
    a = panel["gridPos"]
    assert 0 <= a["x"] and a["x"] + a["w"] <= 24, "Panel outside grid"
    for other in panels[index + 1:]:
        b = other["gridPos"]
        overlap = (a["x"] < b["x"] + b["w"] and b["x"] < a["x"] + a["w"]
                   and a["y"] < b["y"] + b["h"] and b["y"] < a["y"] + a["h"])
        assert not overlap, f"Overlapping panels: {panel['id']}, {other['id']}"

rules = [{"record": f"validate_panel_{p['id']}_{t['refId'].lower()}",
          "expr": t["expr"].replace("$__rate_interval", "5m")}
         for p in panels for t in p.get("targets", [])]

with tempfile.TemporaryDirectory(prefix="ignoa-payment-promql-") as temp:
    directory = Path(temp).resolve()
    (directory / "rules.json").write_text(json.dumps({"groups": [{"name": "dashboard", "rules": rules}]}))
    command = ["docker", "run", "--rm", "--network", "none", "--entrypoint", "/bin/promtool",
               "--mount", f"type=bind,src={directory},dst=/validation,readonly", "prom/prometheus:v3.12.0"]
    subprocess.run(command + ["check", "rules", "/validation/rules.json"], check=True)

print(f"Validated {len(panels)} panels, {len(rules)} queries")
