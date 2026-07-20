#!/usr/bin/env ruby

require "yaml"

module Backlog
  module_function

  ID_PATTERN = /\A(?:SETUP-\d{2}|VS-\d{3}|OPS-\d{2}|E2E-\d{2})\z/

  def load_and_validate(path)
    data = YAML.safe_load(File.read(path), aliases: false)
    raise "root must be a mapping" unless data.is_a?(Hash)
    raise "schema_version must be 1" unless data["schema_version"] == 1
    settings = data["settings"]
    raise "settings must be a mapping" unless settings.is_a?(Hash)
    required_settings = %w[repository base_branch approver merge_command max_parallel_workers review_poll_minutes max_recovery_attempts]
    required_settings.each { |key| raise "missing settings.#{key}" unless settings.key?(key) }
    raise "merge_command must be /merge-approved" unless settings["merge_command"] == "/merge-approved"
    raise "max_parallel_workers must be 1..3" unless settings["max_parallel_workers"].is_a?(Integer) && settings["max_parallel_workers"].between?(1, 3)
    raise "max_recovery_attempts must be 3" unless settings["max_recovery_attempts"] == 3

    tasks = data["tasks"]
    raise "tasks must be a non-empty list" unless tasks.is_a?(Array) && !tasks.empty?
    ids = tasks.map { |task| task["id"] }
    raise "task IDs must be unique" unless ids.uniq.length == ids.length
    tasks.each do |task|
      raise "task must be a mapping" unless task.is_a?(Hash)
      %w[id order title depends_on resource_locks].each { |key| raise "#{task["id"] || "task"} missing #{key}" unless task.key?(key) }
      raise "invalid task id #{task["id"]}" unless task["id"].is_a?(String) && task["id"].match?(ID_PATTERN)
      raise "#{task["id"]} order must be integer" unless task["order"].is_a?(Integer)
      raise "#{task["id"]} title must not be empty" if task["title"].to_s.strip.empty?
      raise "#{task["id"]} depends_on must be a list" unless task["depends_on"].is_a?(Array)
      raise "#{task["id"]} resource_locks must be a non-empty list" unless task["resource_locks"].is_a?(Array) && !task["resource_locks"].empty?
      missing = task["depends_on"] - ids
      raise "#{task["id"]} has unknown dependencies: #{missing.join(", ")}" unless missing.empty?
      raise "#{task["id"]} depends on itself" if task["depends_on"].include?(task["id"])
    end

    initial = data["initial_merged"]
    raise "initial_merged must be a list" unless initial.is_a?(Array)
    unknown_initial = initial - ids
    raise "initial_merged has unknown IDs: #{unknown_initial.join(", ")}" unless unknown_initial.empty?
    detect_cycle!(tasks)
    data
  rescue Errno::ENOENT, Psych::Exception => error
    raise "cannot load backlog: #{error.message}"
  end

  def detect_cycle!(tasks)
    graph = tasks.to_h { |task| [task["id"], task["depends_on"]] }
    visiting = {}
    visited = {}
    visit = lambda do |id|
      raise "dependency cycle includes #{id}" if visiting[id]
      return if visited[id]
      visiting[id] = true
      graph.fetch(id).each { |dependency| visit.call(dependency) }
      visiting.delete(id)
      visited[id] = true
    end
    graph.each_key { |id| visit.call(id) }
  end

  def ready(data, merged:, active:, limit: nil)
    merged_ids = (data["initial_merged"] + merged).uniq
    active_ids = active.map { |item| item.fetch("id") }
    held_locks = active.flat_map { |item| item.fetch("resource_locks", []) }.uniq
    selected_locks = []
    capacity = [limit || data.dig("settings", "max_parallel_workers"), data.dig("settings", "max_parallel_workers") - active.length].min
    return [] if capacity <= 0

    data["tasks"].sort_by { |task| [task["order"], task["id"]] }.each_with_object([]) do |task, result|
      next if merged_ids.include?(task["id"]) || active_ids.include?(task["id"])
      next unless (task["depends_on"] - merged_ids).empty?
      next unless (task["resource_locks"] & (held_locks + selected_locks)).empty?
      result << task
      selected_locks.concat(task["resource_locks"])
      break result if result.length >= capacity
    end
  end
end

if $PROGRAM_NAME == __FILE__
  path = ARGV.first
  abort "usage: validate_backlog.rb <backlog.yml>" unless path
  begin
    data = Backlog.load_and_validate(path)
    puts "VALID: #{data["tasks"].length} backlog tasks"
  rescue StandardError => error
    warn "INVALID: #{error.message}"
    exit 1
  end
end
