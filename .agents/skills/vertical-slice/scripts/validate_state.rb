#!/usr/bin/env ruby

require "yaml"

LEGACY_STATUSES = %w[
  NOT_STARTED PLANNING AWAITING_TEST_APPROVAL IMPLEMENTING AWAITING_CHANGE_APPROVAL
  CREATING_PR AWAITING_PR_REVIEW AWAITING_REVIEW_PLAN_APPROVAL APPLYING_REVIEW_CHANGES
  AWAITING_REVIEW_RECHECK AWAITING_USER_MERGE MERGED_AWAITING_SYNC COMPLETED_SYNCED
].freeze

AUTONOMOUS_STATUSES = %w[
  NOT_STARTED PLANNING IMPLEMENTING CREATING_PR AWAITING_PR_REVIEW
  APPLYING_REVIEW_CHANGES BLOCKED AWAITING_USER_MERGE
  MERGED_AWAITING_SYNC COMPLETED_SYNCED
].freeze

LEGACY_TRANSITIONS = {
  "NOT_STARTED" => %w[PLANNING],
  "PLANNING" => %w[AWAITING_TEST_APPROVAL],
  "AWAITING_TEST_APPROVAL" => %w[IMPLEMENTING],
  "IMPLEMENTING" => %w[AWAITING_CHANGE_APPROVAL],
  "AWAITING_CHANGE_APPROVAL" => %w[CREATING_PR],
  "CREATING_PR" => %w[AWAITING_PR_REVIEW],
  "AWAITING_PR_REVIEW" => %w[AWAITING_REVIEW_PLAN_APPROVAL AWAITING_USER_MERGE],
  "AWAITING_REVIEW_PLAN_APPROVAL" => %w[APPLYING_REVIEW_CHANGES],
  "APPLYING_REVIEW_CHANGES" => %w[AWAITING_REVIEW_RECHECK],
  "AWAITING_REVIEW_RECHECK" => %w[AWAITING_REVIEW_PLAN_APPROVAL AWAITING_USER_MERGE],
  "AWAITING_USER_MERGE" => %w[MERGED_AWAITING_SYNC],
  "MERGED_AWAITING_SYNC" => %w[COMPLETED_SYNCED],
  "COMPLETED_SYNCED" => []
}.freeze

AUTONOMOUS_TRANSITIONS = {
  "NOT_STARTED" => %w[PLANNING BLOCKED],
  "PLANNING" => %w[IMPLEMENTING BLOCKED],
  "IMPLEMENTING" => %w[CREATING_PR BLOCKED],
  "CREATING_PR" => %w[AWAITING_PR_REVIEW BLOCKED],
  "AWAITING_PR_REVIEW" => %w[APPLYING_REVIEW_CHANGES AWAITING_USER_MERGE BLOCKED],
  "APPLYING_REVIEW_CHANGES" => %w[AWAITING_PR_REVIEW BLOCKED],
  "BLOCKED" => %w[PLANNING IMPLEMENTING CREATING_PR AWAITING_PR_REVIEW APPLYING_REVIEW_CHANGES],
  "AWAITING_USER_MERGE" => %w[APPLYING_REVIEW_CHANGES MERGED_AWAITING_SYNC BLOCKED],
  "MERGED_AWAITING_SYNC" => %w[COMPLETED_SYNCED BLOCKED],
  "COMPLETED_SYNCED" => []
}.freeze

PR_REQUIRED = %w[
  AWAITING_PR_REVIEW APPLYING_REVIEW_CHANGES AWAITING_USER_MERGE
  MERGED_AWAITING_SYNC COMPLETED_SYNCED
].freeze

def reject(message)
  warn "INVALID: #{message}"
  exit 1
end

def mapping(parent, key)
  value = parent[key]
  reject("#{key} must be a mapping") unless value.is_a?(Hash)
  value
end

def require_keys(parent, keys, prefix = nil)
  keys.each do |key|
    name = [prefix, key].compact.join(".")
    reject("missing key: #{name}") unless parent.key?(key)
  end
end

def present?(value)
  !value.nil? && !value.to_s.strip.empty?
end

def validate_failure(data)
  failure = mapping(data, "failure")
  require_keys(failure, %w[active category stage command summary evidence retry_condition attempts], "failure")
  reject("failure.active must be boolean") unless [true, false].include?(failure["active"])
  reject("failure.attempts must be an integer from 0 to 3") unless failure["attempts"].is_a?(Integer) && failure["attempts"].between?(0, 3)
  if failure["active"]
    %w[category stage summary retry_condition].each do |key|
      reject("failure.#{key} is required when active") unless present?(failure[key])
    end
  end
end

def validate_history(data, statuses, transitions)
  history = data["history"]
  reject("history must be a list") unless history.is_a?(Array)
  history.each_with_index do |entry, index|
    reject("history[#{index}] must be a mapping") unless entry.is_a?(Hash)
    require_keys(entry, %w[at from to command note], "history[#{index}]")
    reject("history[#{index}].from is unsupported") unless statuses.include?(entry["from"])
    reject("history[#{index}].to is unsupported") unless statuses.include?(entry["to"])
    unless transitions.fetch(entry["from"]).include?(entry["to"])
      reject("history[#{index}] transition #{entry["from"]} -> #{entry["to"]} is not allowed")
    end
    if index.positive? && history[index - 1]["to"] != entry["from"]
      reject("history[#{index}] does not continue from the previous transition")
    end
  end
  status = data["status"]
  if history.empty?
    reject("history is required when status is not NOT_STARTED") unless status == "NOT_STARTED"
  elsif history.last["to"] != status
    reject("last history transition must end at #{status}")
  end
end

def validate_legacy(data, path)
  slice = mapping(data, "slice")
  id = slice["id"]
  reject("slice.id must match VS-NNN") unless id.is_a?(String) && id.match?(/\AVS-\d{3}\z/)
  reject("slice.title must not be empty") unless present?(slice["title"])
  reject("filename must match slice.id") unless [id, "slice-state"].include?(File.basename(path, File.extname(path)))
  status = data["status"]
  reject("unsupported legacy status: #{status}") unless LEGACY_STATUSES.include?(status)
  require_keys(data, %w[branch base_commit current_commit created_at updated_at])
  approvals = mapping(data, "approvals")
  %w[test change review_plan].each do |name|
    approval = mapping(approvals, name)
    require_keys(approval, %w[approved approved_at scope_sha256], "approvals.#{name}")
  end
  review = mapping(data, "review")
  require_keys(review, %w[pr_number pr_url handled_comment_ids], "review")
  evidence = mapping(data, "evidence")
  require_keys(evidence, %w[red_test focused_tests full_tests migration ci_url ci_result merge_commit synced_main_commit], "evidence")
  validate_failure(data)
  validate_history(data, LEGACY_STATUSES, LEGACY_TRANSITIONS)
  puts "VALID: #{id} is #{status} (legacy schema)"
end

def validate_autonomous(data, path)
  task = mapping(data, "task")
  require_keys(task, %w[id title dependencies resource_locks], "task")
  id = task["id"]
  reject("task.id must match VS-NNN, OPS-NN, or E2E-NN") unless id.is_a?(String) && id.match?(/\A(?:VS-\d{3}|OPS-\d{2}|E2E-\d{2})\z/)
  reject("task.title must not be empty") unless present?(task["title"])
  reject("task.dependencies must be a list") unless task["dependencies"].is_a?(Array)
  reject("task.resource_locks must be a list") unless task["resource_locks"].is_a?(Array)
  reject("filename must match task.id") unless [id, "slice-state"].include?(File.basename(path, File.extname(path)))

  status = data["status"]
  reject("unsupported autonomous status: #{status}") unless AUTONOMOUS_STATUSES.include?(status)
  require_keys(data, %w[branch base_commit current_commit created_at updated_at])

  if data.key?("delivery")
    delivery = mapping(data, "delivery")
    require_keys(delivery, %w[strategy parent_task_id parent_pr_number base_ref base_head_sha stack_root stack_depth merge_order], "delivery")
    reject("delivery.strategy must be main or stacked_pr") unless %w[main stacked_pr].include?(delivery["strategy"])
    reject("delivery.stack_depth must be 1..8") unless delivery["stack_depth"].is_a?(Integer) && delivery["stack_depth"].between?(1, 8)
    reject("delivery.merge_order must be a list") unless delivery["merge_order"].is_a?(Array)
    if delivery["strategy"] == "stacked_pr"
      reject("delivery.parent_task_id is required for stacked_pr") unless present?(delivery["parent_task_id"])
      reject("delivery.parent_pr_number is required for stacked_pr") unless delivery["parent_pr_number"].is_a?(Integer)
      reject("delivery.base_ref is required for stacked_pr") unless present?(delivery["base_ref"])
      reject("delivery.base_head_sha is required for stacked_pr") unless present?(delivery["base_head_sha"])
      reject("delivery.stack_depth must be greater than 1 for stacked_pr") unless delivery["stack_depth"] > 1
    end
  end

  review = mapping(data, "review")
  require_keys(review, %w[pr_number pr_url handled_comment_ids unresolved_thread_count merge_approval], "review")
  reject("review.handled_comment_ids must be a list") unless review["handled_comment_ids"].is_a?(Array)
  approval = mapping(review, "merge_approval")
  require_keys(approval, %w[command approved_by comment_id comment_url approved_at head_sha], "review.merge_approval")
  reject("merge approval command must be /merge-approved") unless approval["command"] == "/merge-approved"

  evidence = mapping(data, "evidence")
  require_keys(evidence, %w[dependencies_satisfied dependency_prs red_test focused_tests full_tests migration ci_url ci_result merge_commit synced_main_commit], "evidence")
  reject("evidence.dependencies_satisfied must be boolean") unless [true, false].include?(evidence["dependencies_satisfied"])
  reject("evidence.dependency_prs must be a list") unless evidence["dependency_prs"].is_a?(Array)

  if PR_REQUIRED.include?(status)
    reject("review.pr_number is required for #{status}") unless review["pr_number"].is_a?(Integer)
    reject("review.pr_url is required for #{status}") unless present?(review["pr_url"])
  end

  if status == "AWAITING_USER_MERGE"
    reject("dependencies must be satisfied") unless evidence["dependencies_satisfied"] == true
    reject("successful CI evidence is required") unless evidence["ci_result"] == "success"
    reject("unresolved review threads must be zero") unless review["unresolved_thread_count"] == 0
    reject("merge approval must come from pado0711") unless approval["approved_by"] == "pado0711"
    %w[comment_id comment_url approved_at head_sha].each do |key|
      reject("review.merge_approval.#{key} is required") unless present?(approval[key])
    end
    reject("merge approval is stale for current_commit") unless approval["head_sha"] == data["current_commit"]
  end

  validate_failure(data)
  reject("AWAITING_USER_MERGE cannot have an active failure") if status == "AWAITING_USER_MERGE" && data.dig("failure", "active")
  validate_history(data, AUTONOMOUS_STATUSES, AUTONOMOUS_TRANSITIONS)
  puts "VALID: #{id} is #{status}"
end

path = ARGV.first
reject("usage: validate_state.rb <state-file>") unless present?(path)
reject("file not found: #{path}") unless File.file?(path)

begin
  data = YAML.safe_load(File.read(path), aliases: false)
rescue Psych::Exception => error
  reject("invalid YAML: #{error.message}")
end

reject("root must be a mapping") unless data.is_a?(Hash)
case data["schema_version"]
when 1 then validate_legacy(data, path)
when 2 then validate_autonomous(data, path)
else reject("schema_version must be 1 or 2")
end
