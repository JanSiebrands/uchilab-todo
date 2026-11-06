# Pure evaluator for tools/ci/main-run-status.sh: reads the JSON array of `gh run list --json
# databaseId,status,conclusion,event,createdAt,url,displayTitle` from stdin and prints one line:
#   GREEN|RED <workflow> <url of the latest run> since=<createdAt> count=<n>
#   ERROR <workflow> <reason>            (no completed run or unknown conclusion; never looks green)
# Only the latest completed run decides. cancelled and skipped runs are ignored. since/count describe the
# current series of runs with the same result (at most as many runs as the query returned).
# Argument: --arg wf <workflow>
def color:
  if . == "success" or . == "neutral" then "GREEN"
  elif . == "failure" or . == "timed_out" or . == "startup_failure" then "RED"
  else "UNKNOWN" end;

[ .[] | select(.status == "completed" and .conclusion != "cancelled" and .conclusion != "skipped") ]
| sort_by(.createdAt) | reverse
| if length == 0 then "ERROR \($wf) no completed run"
  else
    (.[0].conclusion | color) as $c
    | if $c == "UNKNOWN" then "ERROR \($wf) unknown conclusion \(.[0].conclusion) \(.[0].url)"
      else
        ([ .[] | (.conclusion | color) == $c ] | (indices(false) | first) // length) as $n
        | "\($c) \($wf) \(.[0].url) since=\(.[$n - 1].createdAt) count=\($n)"
      end
  end
