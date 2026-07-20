#!/usr/bin/env ruby

require "fileutils"
require "minitest/autorun"
require "tmpdir"
require "yaml"
require_relative "check_merge_guard"

class VerticalSliceMergeGuardTest < Minitest::Test
  def test_non_slice_branch_passes_without_state_file
    passed, = check("codex/pre-merge-guard")

    assert passed
  end

  def test_slice_branch_is_blocked_before_user_merge_state
    write_state(status: "AWAITING_PR_REVIEW")

    passed, message = check("codex/vs-004-logout")

    refute passed
    assert_includes message, "status must be AWAITING_USER_MERGE"
  end

  def test_slice_branch_passes_when_all_merge_evidence_is_ready
    write_state(status: "AWAITING_USER_MERGE")

    passed, message = check("codex/vs-004-logout")

    assert passed
    assert_equal "PASS: VS-004 is ready for user merge", message
  end

  def test_slice_branch_is_blocked_when_state_branch_does_not_match
    write_state(status: "AWAITING_USER_MERGE", branch: "codex/vs-004-other")

    passed, message = check("codex/vs-004-logout")

    refute passed
    assert_includes message, "branch must be codex/vs-004-logout"
  end

  def test_slice_branch_is_blocked_when_state_file_is_missing
    passed, message = check("codex/vs-004-logout")

    refute passed
    assert_includes message, "missing state file"
  end

  def test_slice_branch_requires_the_full_state_schema
    write_state(status: "AWAITING_USER_MERGE")

    passed, message = VerticalSliceMergeGuard.check(
      head_ref: "codex/vs-004-logout",
      repo_root: @repo_root
    )

    refute passed
    assert_includes message, "invalid slice state"
  end

  private

  def setup
    @repo_root = Dir.mktmpdir("merge-guard-test")
  end

  def teardown
    FileUtils.remove_entry(@repo_root)
  end

  def check(head_ref)
    VerticalSliceMergeGuard.check(
      head_ref: head_ref,
      repo_root: @repo_root,
      validate_schema: false
    )
  end

  def write_state(status:, branch: "codex/vs-004-logout")
    directory = File.join(@repo_root, "docs", "workflow", "slices")
    FileUtils.mkdir_p(directory)
    state = {
      "slice" => { "id" => "VS-004" },
      "status" => status,
      "branch" => branch,
      "approvals" => {
        "test" => { "approved" => true },
        "change" => { "approved" => true }
      },
      "evidence" => { "ci_result" => "success" },
      "failure" => { "active" => false }
    }
    File.write(File.join(directory, "VS-004.yml"), state.to_yaml)
  end
end
