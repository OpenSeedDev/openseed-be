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
        "review_poll_minutes" => 5, "max_recovery_attempts" => 3,
        "delivery_mode" => "safe_merge", "dependency_strategy" => "merged_only",
        "review_strategy" => "immediate", "max_stack_depth" => 4,
        "full_test_checkpoint_size" => 4, "fast_build_exit" => "all_vs_tasks_have_pr"
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

  def test_selects_exactly_one_task_when_single_task_mode_is_configured
    @data["settings"]["max_parallel_workers"] = 1

    assert_equal ["VS-001"], Backlog.ready(load_data, merged: [], active: []).map { |task| task["id"] }
  end

  def test_unlocks_dependency_only_after_merge
    data = load_data
    assert_includes Backlog.ready(data, merged: ["VS-001"], active: []).map { |task| task["id"] }, "VS-003"
  end

  def test_fast_build_stacks_a_successor_on_an_open_dependency
    @data["settings"].merge!(
      "delivery_mode" => "fast_build",
      "dependency_strategy" => "stacked_pr",
      "review_strategy" => "deferred"
    )
    open = [{
      "id" => "VS-001", "resource_locks" => ["member"],
      "head_ref" => "codex/vs-001", "pr_number" => 101,
      "stack_root" => "VS-001", "stack_depth" => 1
    }]

    selected = Backlog.ready(load_data, merged: [], active: [], open: open)
    successor = selected.find { |task| task["id"] == "VS-003" }

    refute_nil successor
    assert_equal "codex/vs-001", successor.dig("delivery", "base_ref")
    assert_equal "VS-001", successor.dig("delivery", "parent_id")
    assert_equal 2, successor.dig("delivery", "stack_depth")
  end

  def test_fast_build_does_not_count_open_prs_as_worker_slots
    @data["settings"].merge!(
      "delivery_mode" => "fast_build",
      "dependency_strategy" => "stacked_pr",
      "review_strategy" => "deferred"
    )
    open = [{
      "id" => "VS-001", "resource_locks" => ["member"],
      "head_ref" => "codex/vs-001", "stack_depth" => 1
    }]

    selected = Backlog.ready(load_data, merged: [], active: [], open: open)

    assert_equal 2, selected.length
  end

  def test_fast_build_stops_a_lane_at_the_stack_depth_limit
    @data["settings"].merge!(
      "delivery_mode" => "fast_build",
      "dependency_strategy" => "stacked_pr",
      "review_strategy" => "deferred",
      "max_stack_depth" => 1
    )
    open = [{
      "id" => "VS-001", "resource_locks" => ["member"],
      "head_ref" => "codex/vs-001", "stack_depth" => 1
    }]

    selected_ids = Backlog.ready(load_data, merged: [], active: [], open: open).map { |task| task["id"] }

    refute_includes selected_ids, "VS-003"
  end

  def test_fast_build_completion_ignores_non_vs_tasks
    open = [{ "id" => "VS-001" }, { "id" => "VS-002" }, { "id" => "VS-003" }]

    assert Backlog.fast_build_complete?(load_data, merged: [], open: open)
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
