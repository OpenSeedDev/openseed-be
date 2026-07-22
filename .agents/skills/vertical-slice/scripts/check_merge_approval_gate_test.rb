#!/usr/bin/env ruby

require "minitest/autorun"
require_relative "check_merge_approval_gate"

class MergeApprovalGateTest < Minitest::Test
  def test_accepts_current_main_with_successful_required_checks
    assert MergeApprovalGate.validate!(
      pr_base_sha: "main-sha",
      latest_base_sha: "main-sha",
      check_runs: successful_checks
    )
  end

  def test_rejects_a_branch_behind_main_even_when_checks_succeeded
    error = assert_raises(RuntimeError) do
      MergeApprovalGate.validate!(
        pr_base_sha: "old-main-sha",
        latest_base_sha: "latest-main-sha",
        check_runs: successful_checks
      )
    end
    assert_includes error.message, "behind main"
  end

  def test_rejects_missing_required_check
    error = assert_raises(RuntimeError) do
      MergeApprovalGate.validate!(
        pr_base_sha: "main-sha",
        latest_base_sha: "main-sha",
        check_runs: [successful_check("Build and Test")]
      )
    end
    assert_includes error.message, "Vertical Slice Merge Guard"
  end

  def test_rejects_pending_or_failed_required_check
    checks = [
      successful_check("Build and Test"),
      { "name" => "Vertical Slice Merge Guard", "status" => "completed", "conclusion" => "failure" }
    ]
    error = assert_raises(RuntimeError) do
      MergeApprovalGate.validate!(
        pr_base_sha: "main-sha",
        latest_base_sha: "main-sha",
        check_runs: checks
      )
    end
    assert_includes error.message, "not successful"
  end

  private

  def successful_checks
    MergeApprovalGate::REQUIRED_CHECKS.map { |name| successful_check(name) }
  end

  def successful_check(name)
    { "name" => name, "status" => "completed", "conclusion" => "success" }
  end
end
