#!/usr/bin/env ruby

require "minitest/autorun"
require "tempfile"
require "yaml"
require_relative "validate_backlog"

class BacklogTest < Minitest::Test
  def setup
    @data = {
      "schema_version" => 1,
      "settings" => {
        "repository" => "owner/repo", "base_branch" => "main", "approver" => "owner",
        "merge_command" => "/merge-approved", "max_parallel_workers" => 3,
        "review_poll_minutes" => 5, "max_recovery_attempts" => 3
      },
      "initial_merged" => ["SETUP-01"],
      "tasks" => [
        { "id" => "SETUP-01", "order" => 1, "title" => "base", "depends_on" => [], "resource_locks" => ["platform"] },
        { "id" => "VS-001", "order" => 2, "title" => "one", "depends_on" => ["SETUP-01"], "resource_locks" => ["member"] },
        { "id" => "VS-002", "order" => 3, "title" => "two", "depends_on" => ["SETUP-01"], "resource_locks" => ["wallet"] },
        { "id" => "VS-003", "order" => 4, "title" => "three", "depends_on" => ["VS-001"], "resource_locks" => ["idea"] }
      ]
    }
  end

  def test_selects_independent_ready_tasks_in_order
    data = load_data
    assert_equal %w[VS-001 VS-002], Backlog.ready(data, merged: [], active: []).map { |task| task["id"] }
  end

  def test_respects_active_locks_and_worker_capacity
    data = load_data
    active = [{ "id" => "OTHER", "resource_locks" => ["member"] }]
    assert_equal ["VS-002"], Backlog.ready(data, merged: [], active: active).map { |task| task["id"] }
  end

  def test_unlocks_dependency_only_after_merge
    data = load_data
    assert_includes Backlog.ready(data, merged: ["VS-001"], active: []).map { |task| task["id"] }, "VS-003"
  end

  def test_rejects_dependency_cycle
    @data["tasks"][0]["depends_on"] = ["VS-003"]
    error = assert_raises(RuntimeError) { load_data }
    assert_includes error.message, "dependency cycle"
  end

  private

  def load_data
    file = Tempfile.new(["backlog", ".yml"])
    file.write(@data.to_yaml)
    file.close
    Backlog.load_and_validate(file.path)
  ensure
    file&.unlink
  end
end
