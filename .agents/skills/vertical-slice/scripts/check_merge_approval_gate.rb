#!/usr/bin/env ruby

require "json"
require "optparse"

module MergeApprovalGate
  REQUIRED_CHECKS = ["Build and Test", "Vertical Slice Merge Guard"].freeze

  module_function

  def validate!(pr_base_sha:, latest_base_sha:, check_runs:)
    unless pr_base_sha == latest_base_sha
      raise "PR branch is behind main; update the branch and rerun required checks before /merge-approved"
    end

    REQUIRED_CHECKS.each do |name|
      matching_runs = check_runs.select { |run| run["name"] == name }
      successful = matching_runs.any? do |run|
        run["status"] == "completed" && run["conclusion"] == "success"
      end
      next if successful

      raise "required check is not successful for the current head: #{name}"
    end

    true
  end
end

if $PROGRAM_NAME == __FILE__
  begin
    options = {}
    OptionParser.new do |parser|
      parser.on("--pr-base-sha SHA") { |value| options[:pr_base_sha] = value }
      parser.on("--latest-base-sha SHA") { |value| options[:latest_base_sha] = value }
      parser.on("--check-runs-file PATH") { |value| options[:check_runs_file] = value }
    end.parse!

    required = %i[pr_base_sha latest_base_sha check_runs_file]
    missing = required.reject { |key| options[key] && !options[key].empty? }
    raise "missing options: #{missing.join(', ')}" unless missing.empty?

    payload = JSON.parse(File.read(options[:check_runs_file]))
    MergeApprovalGate.validate!(
      pr_base_sha: options[:pr_base_sha],
      latest_base_sha: options[:latest_base_sha],
      check_runs: payload.fetch("check_runs")
    )
    puts "PASS: main is current and required checks succeeded before approval"
  rescue StandardError => error
    warn "BLOCKED: #{error.message}"
    exit 1
  end
end
