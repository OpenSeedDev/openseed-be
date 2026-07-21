#!/usr/bin/env ruby

require "minitest/autorun"
require "open3"
require "rbconfig"

class SelectBackendTestScopeTest < Minitest::Test
  SCRIPT = File.expand_path("select_backend_test_scope.rb", __dir__)

  def test_runs_full_tests_for_production_code
    assert_equal "full", scope_for("src/main/java/com/seedrank/member/Member.java\n")
  end

  def test_runs_full_tests_for_tests_migrations_and_build_files
    %w[
      src/test/java/com/seedrank/member/MemberTest.java
      src/main/resources/db/migration/V10__member.sql
      build.gradle
      settings.gradle
      gradle/wrapper/gradle-wrapper.properties
      gradlew
    ].each do |path|
      assert_equal "full", scope_for("#{path}\n"), path
    end
  end

  def test_uses_lightweight_validation_for_workflow_state_and_docs
    paths = <<~PATHS
      docs/workflow/slices/VS-031.yml
      docs/workflow/lessons/workflow-failures.yml
      docs/architecture/erd.md
      .agents/skills/vertical-slice/SKILL.md
      .github/workflows/backend-ci.yml
    PATHS

    assert_equal "workflow-only", scope_for(paths)
  end

  def test_uses_full_tests_when_any_code_file_is_mixed_with_docs
    paths = "docs/workflow/slices/VS-031.yml\nsrc/main/java/com/seedrank/point/PointWallet.java\n"

    assert_equal "full", scope_for(paths)
  end

  def test_empty_or_unreadable_diff_fails_safe
    assert_equal "full", scope_for("")
  end

  private

  def scope_for(paths)
    output, error, status = Open3.capture3(RbConfig.ruby, SCRIPT, stdin_data: paths)
    assert status.success?, error
    output.strip
  end
end
