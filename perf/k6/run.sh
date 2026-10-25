#!/usr/bin/env bash
set -euo pipefail

# 예: ./perf/k6/run.sh browse        워밍업 1회 + 본 측정 3회
#     ./perf/k6/run.sh target-load once   1회만 (마감 동시 테스트 등)
usage='사용법: ./perf/k6/run.sh {browse|target-load|stress} [once]'
scenario="${1:-}"
case "$scenario" in
  browse|target-load|stress) ;;
  *) echo "$usage" >&2; exit 1 ;;
esac
mode="${2:-}"
if [[ -n "$mode" && "$mode" != once ]] || (( $# > 2 )); then
  echo "$usage" >&2
  exit 1
fi
if [[ "$mode" == once ]]; then runs=(1); else runs=(warmup 1 2 3); fi
last_run="${runs[${#runs[@]}-1]}"

k6_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
set -a
source "$k6_dir/k6.env"
set +a

result_root="$(dirname -- "$k6_dir")/results/k6/$(date '+%F')"
mkdir -p "$result_root"
result_dir="$(mktemp -d "$result_root/${scenario}${mode:+-$mode}-$(date '+%H%M%S')-XXXXXX")"
echo "결과 경로: $result_dir"

threshold_failed_runs=()
for run_id in "${runs[@]}"; do
  echo "[$(date '+%F %T %Z')] 시작: $scenario / $run_id"
  set +e
  SUMMARY_FILE="$result_dir/$run_id.json" k6 run "$k6_dir/scenarios/$scenario.js" 2>&1 | tee "$result_dir/$run_id.log"
  run_status=("${PIPESTATUS[@]}")
  set -e
  if (( run_status[1] != 0 )); then
    echo "로그 저장 오류로 중단: $result_dir/$run_id.log" >&2
    exit "${run_status[1]}"
  fi
  if (( run_status[0] == 99 )); then
    threshold_failed_runs+=("$run_id")
    echo "threshold 미달 (다음 회차 계속): $result_dir/$run_id.log" >&2
  elif (( run_status[0] != 0 )); then
    echo "실행 오류로 중단 (종료 코드 ${run_status[0]}): $result_dir/$run_id.log" >&2
    exit "${run_status[0]}"
  fi
  echo "[$(date '+%F %T %Z')] 완료: $run_id"
  if [[ "$run_id" != "$last_run" ]]; then
    echo '다음 실행까지 2분 대기'
    sleep 120
  fi
done

if [[ "$mode" == once ]]; then
  echo "완료: $result_dir"
else
  echo "완료: $result_dir (warmup 제외, 1·2·3이 본 측정)"
fi
if (( ${#threshold_failed_runs[@]} > 0 )); then
  echo "threshold 미달 회차: ${threshold_failed_runs[*]} (각 회차 JSON·로그 확인)" >&2
fi
