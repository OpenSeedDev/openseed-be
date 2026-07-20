#!/usr/bin/env ruby

require "optparse"
require "open3"
require "rbconfig"
require "yaml"

class VerticalSliceMergeGuard
  FEATURE_BRANCH = %r{\Acodex/vs-(\d{3})-[a-z0-9][a-z0-9-]*\z}

  def self.check(head_ref:, repo_root:, validate_schema: true)
    match = FEATURE_BRANCH.match(head_ref)
    return [true, "PASS: #{head_ref} is not a vertical-slice feature branch"] unless match

    slice_id = "VS-#{match[1]}"
    state_path = File.join(repo_root, "docs", "workflow", "slices", "#{slice_id}.yml")
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
    errors << "slice.id must be #{slice_id}" unless state.dig("slice", "id") == slice_id
    errors << "branch must be #{head_ref}" unless state["branch"] == head_ref
    errors << "status must be AWAITING_USER_MERGE" unless state["status"] == "AWAITING_USER_MERGE"
    errors << "test approval is missing" unless state.dig("approvals", "test", "approved") == true
    errors << "change approval is missing" unless state.dig("approvals", "change", "approved") == true
    errors << "CI evidence must be success" unless state.dig("evidence", "ci_result") == "success"
    errors << "an active workflow failure remains" unless state.dig("failure", "active") == false

    if errors.empty?
      [true, "PASS: #{slice_id} is ready for user merge"]
    else
      [false, "BLOCKED: #{slice_id} is not ready for merge: #{errors.join('; ')}"]
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
