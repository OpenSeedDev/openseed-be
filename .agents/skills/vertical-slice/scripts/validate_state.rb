#!/usr/bin/env ruby

require "yaml"

STATUSES = %w[
  NOT_STARTED
  PLANNING
  AWAITING_TEST_APPROVAL
  IMPLEMENTING
  AWAITING_CHANGE_APPROVAL
  CREATING_PR
  AWAITING_PR_REVIEW
  AWAITING_REVIEW_PLAN_APPROVAL
  APPLYING_REVIEW_CHANGES
  AWAITING_REVIEW_RECHECK
  AWAITING_USER_MERGE
  MERGED_AWAITING_SYNC
  COMPLETED_SYNCED
].freeze

TRANSITIONS = {
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

TEST_APPROVAL_REQUIRED = STATUSES - %w[NOT_STARTED PLANNING AWAITING_TEST_APPROVAL]
CHANGE_APPROVAL_REQUIRED = %w[
  CREATING_PR
  AWAITING_PR_REVIEW
  AWAITING_REVIEW_PLAN_APPROVAL
  APPLYING_REVIEW_CHANGES
  AWAITING_REVIEW_RECHECK
  AWAITING_USER_MERGE
  MERGED_AWAITING_SYNC
  COMPLETED_SYNCED
].freeze
PR_REQUIRED = %w[
  AWAITING_PR_REVIEW
  AWAITING_REVIEW_PLAN_APPROVAL
  APPLYING_REVIEW_CHANGES
  AWAITING_REVIEW_RECHECK
  AWAITING_USER_MERGE
  MERGED_AWAITING_SYNC
  COMPLETED_SYNCED
].freeze

def reject(message)
  warn "INVALID: #{message}"
  exit 1
end

def require_hash(parent, key)
  value = parent[key]
  reject("#{key} must be a mapping") unless value.is_a?(Hash)
  value
end

def require_key(parent, key)
  reject("missing key: #{key}") unless parent.key?(key)
  parent[key]
end

path = ARGV.first
reject("usage: validate_state.rb <state-file>") if path.nil? || path.empty?
reject("file not found: #{path}") unless File.file?(path)

begin
  data = YAML.safe_load(File.read(path), aliases: false)
rescue Psych::Exception => error
  reject("invalid YAML: #{error.message}")
end

reject("root must be a mapping") unless data.is_a?(Hash)
reject("schema_version must be 1") unless data["schema_version"] == 1

slice = require_hash(data, "slice")
slice_id = require_key(slice, "id")
reject("slice.id must match VS-NNN") unless slice_id.is_a?(String) && slice_id.match?(/\AVS-\d{3}\z/)
reject("slice.title must not be empty") unless slice["title"].is_a?(String) && !slice["title"].strip.empty?

file_id = File.basename(path, File.extname(path))
reject("filename #{file_id} must match slice.id #{slice_id}") unless file_id == slice_id || file_id == "slice-state"

status = require_key(data, "status")
reject("unsupported status: #{status}") unless STATUSES.include?(status)

%w[branch base_commit current_commit created_at updated_at].each do |key|
  require_key(data, key)
end

approvals = require_hash(data, "approvals")
%w[test change review_plan].each do |approval_name|
  approval = require_hash(approvals, approval_name)
  approved = require_key(approval, "approved")
  reject("approvals.#{approval_name}.approved must be boolean") unless [true, false].include?(approved)
  require_key(approval, "approved_at")
  require_key(approval, "scope_sha256")
  if approved
    reject("approvals.#{approval_name}.approved_at is required") if approval["approved_at"].nil?
    reject("approvals.#{approval_name}.scope_sha256 is required") if approval["scope_sha256"].nil?
  end
end

if TEST_APPROVAL_REQUIRED.include?(status) && approvals.dig("test", "approved") != true
  reject("test approval is required for #{status}")
end

if CHANGE_APPROVAL_REQUIRED.include?(status) && approvals.dig("change", "approved") != true
  reject("change approval is required for #{status}")
end

if %w[APPLYING_REVIEW_CHANGES AWAITING_REVIEW_RECHECK].include?(status) && approvals.dig("review_plan", "approved") != true
  reject("review plan approval is required for #{status}")
end

review = require_hash(data, "review")
%w[pr_number pr_url handled_comment_ids].each { |key| require_key(review, key) }
reject("review.handled_comment_ids must be a list") unless review["handled_comment_ids"].is_a?(Array)

if PR_REQUIRED.include?(status)
  reject("review.pr_number is required for #{status}") if review["pr_number"].nil?
  reject("review.pr_url is required for #{status}") if review["pr_url"].nil?
end

evidence = require_hash(data, "evidence")
%w[red_test focused_tests full_tests migration ci_url ci_result merge_commit synced_main_commit].each do |key|
  require_key(evidence, key)
end

if status == "AWAITING_USER_MERGE" && evidence["ci_result"] != "success"
  reject("successful CI evidence is required for AWAITING_USER_MERGE")
end

if %w[MERGED_AWAITING_SYNC COMPLETED_SYNCED].include?(status) && evidence["merge_commit"].nil?
  reject("merge commit evidence is required for #{status}")
end

if status == "COMPLETED_SYNCED" && evidence["synced_main_commit"].nil?
  reject("synced main commit evidence is required for COMPLETED_SYNCED")
end

failure = require_hash(data, "failure")
%w[active category stage command summary evidence retry_condition attempts].each do |key|
  require_key(failure, key)
end
reject("failure.active must be boolean") unless [true, false].include?(failure["active"])
reject("failure.attempts must be a non-negative integer") unless failure["attempts"].is_a?(Integer) && failure["attempts"] >= 0

if failure["active"]
  %w[category stage summary retry_condition].each do |key|
    reject("failure.#{key} is required when failure is active") if failure[key].nil? || failure[key].to_s.strip.empty?
  end
end

history = require_key(data, "history")
reject("history must be a list") unless history.is_a?(Array)

history.each_with_index do |entry, index|
  reject("history[#{index}] must be a mapping") unless entry.is_a?(Hash)
  %w[at from to command note].each do |key|
    reject("history[#{index}] missing #{key}") unless entry.key?(key)
  end
  from = entry["from"]
  to = entry["to"]
  reject("history[#{index}] has unsupported from status") unless STATUSES.include?(from)
  reject("history[#{index}] has unsupported to status") unless STATUSES.include?(to)
  reject("history[#{index}] transition #{from} -> #{to} is not allowed") unless TRANSITIONS.fetch(from).include?(to)

  if index.positive? && history[index - 1]["to"] != from
    reject("history[#{index}] does not continue from previous transition")
  end
end

if history.empty?
  reject("history is required when status is not NOT_STARTED") unless status == "NOT_STARTED"
elsif history.last["to"] != status
  reject("last history transition must end at current status #{status}")
end

puts "VALID: #{slice_id} is #{status}"
