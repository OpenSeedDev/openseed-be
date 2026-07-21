#!/usr/bin/env ruby

require "optparse"
require "open3"
require "rbconfig"
require "yaml"

class VerticalSliceMergeGuard
  TASK_BRANCH = %r{\Acodex/(vs-\d{3}|ops-\d{2}|e2e-\d{2})-[a-z0-9][a-z0-9-]*\z}

  def self.check(head_ref:, repo_root:, validate_schema: true)
    match = TASK_BRANCH.match(head_ref)
    return [true, "PASS: #{head_ref} is not an autonomous task branch"] unless match

    task_id = match[1].upcase
    state_path = File.join(repo_root, "docs", "workflow", "slices", "#{task_id}.yml")
    return [false, "BLOCKED: missing state file #{state_path}"] unless File.file?(state_path)

    if validate_schema
      validator = File.join(__dir__, "validate_state.rb")
      _, error, result = Open3.capture3(RbConfig.ruby, validator, state_path)
      return [false, "BLOCKED: invalid slice state: #{error.strip}"] unless result.success?
    end

    begin
      state = YAML.safe_load(File.read(state_path), aliases: false)
    rescue Psych::Exception => error
      return [false, "BLOCKED: invalid YAML in #{state_path}: #{error.message}"]
    end

    errors = []
    state_id = state["schema_version"] == 2 ? state.dig("task", "id") : state.dig("slice", "id")
    errors << "task ID must be #{task_id}" unless state_id == task_id
    errors << "branch must be #{head_ref}" unless state["branch"] == head_ref
    review_ready_statuses = %w[AWAITING_PR_REVIEW AWAITING_USER_MERGE]
    unless review_ready_statuses.include?(state["status"])
      errors << "status must be AWAITING_PR_REVIEW or AWAITING_USER_MERGE"
    end
    errors << "an active workflow failure remains" unless state.dig("failure", "active") == false

    if state["schema_version"] == 2
      errors << "dependencies are not satisfied" unless state.dig("evidence", "dependencies_satisfied") == true
    else
      errors << "test approval is missing" unless state.dig("approvals", "test", "approved") == true
      errors << "change approval is missing" unless state.dig("approvals", "change", "approved") == true
    end

    if errors.empty?
      [true, "PASS: #{task_id} is ready for protected merge"]
    else
      [false, "BLOCKED: #{task_id} is not ready for merge: #{errors.join('; ')}"]
    end
  end
end

if $PROGRAM_NAME == __FILE__
  options = { repo_root: Dir.pwd }
  OptionParser.new do |parser|
    parser.on("--head-ref HEAD_REF") { |value| options[:head_ref] = value }
    parser.on("--repo-root PATH") { |value| options[:repo_root] = value }
  end.parse!

  if options[:head_ref].nil? || options[:head_ref].empty?
    warn "BLOCKED: --head-ref is required"
    exit 1
  end

  passed, message = VerticalSliceMergeGuard.check(**options)
  stream = passed ? $stdout : $stderr
  stream.puts(message)
  exit(passed ? 0 : 1)
end
