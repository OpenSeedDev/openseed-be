#!/usr/bin/env ruby

require "fileutils"
require "minitest/autorun"
require "tmpdir"
require "yaml"
require_relative "check_merge_guard"

class VerticalSliceMergeGuardTest < Minitest::Test
  def setup
    @repo_root = Dir.mktmpdir("merge-guard-test")
  end

  def teardown
    FileUtils.remove_entry(@repo_root)
  end

  def test_non_task_branch_passes
    passed, = check("codex/workflow-change")
    assert passed
  end

  def test_blocks_before_merge_ready_status
    write_state(status: "CREATING_PR")
    passed, message = check("codex/vs-055-profile-id")
    refute passed
    assert_includes message, "status must be AWAITING_PR_REVIEW or AWAITING_USER_MERGE"
  end

  def test_passes_at_review_ready_without_runtime_evidence_commit
    write_state(
      status: "AWAITING_PR_REVIEW",
      ci_result: nil,
      approval_sha: nil,
      approved_by: nil,
      unresolved: nil
    )
    passed, message = check("codex/vs-055-profile-id")
    assert passed
    assert_equal "PASS: VS-055 is ready for protected merge", message
  end

  def test_blocks_unsatisfied_dependencies
    write_state(status: "AWAITING_PR_REVIEW", dependencies_satisfied: false)
    passed, message = check("codex/vs-055-profile-id")
    refute passed
    assert_includes message, "dependencies are not satisfied"
  end

  def test_blocks_active_failure
    write_state(status: "AWAITING_PR_REVIEW", failure_active: true)
    passed, message = check("codex/vs-055-profile-id")
    refute passed
    assert_includes message, "an active workflow failure remains"
  end

  def test_supports_ops_task_branches
    write_state(status: "AWAITING_USER_MERGE", id: "OPS-01", branch: "codex/ops-01-health")
    passed, = check("codex/ops-01-health")
    assert passed
  end

  private

  def check(head_ref)
    VerticalSliceMergeGuard.check(head_ref: head_ref, repo_root: @repo_root, validate_schema: false)
  end

  def write_state(
    status:,
    id: "VS-055",
    branch: "codex/vs-055-profile-id",
    approval_sha: "abc123",
    approved_by: "pado0711",
    unresolved: 0,
    ci_result: "success",
    dependencies_satisfied: true,
    failure_active: false
  )
    directory = File.join(@repo_root, "docs", "workflow", "slices")
    FileUtils.mkdir_p(directory)
    state = {
      "schema_version" => 2,
      "task" => { "id" => id },
      "status" => status,
      "branch" => branch,
      "current_commit" => "abc123",
      "review" => {
        "unresolved_thread_count" => unresolved,
        "merge_approval" => { "approved_by" => approved_by, "head_sha" => approval_sha }
      },
      "evidence" => { "dependencies_satisfied" => dependencies_satisfied, "ci_result" => ci_result },
      "failure" => { "active" => failure_active }
    }
    File.write(File.join(directory, "#{id}.yml"), state.to_yaml)
  end
end
