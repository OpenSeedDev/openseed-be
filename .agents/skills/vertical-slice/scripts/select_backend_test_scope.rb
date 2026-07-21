#!/usr/bin/env ruby

FULL_TEST_PATHS = [
  %r{\Asrc/},
  %r{\Abuild\.gradle(?:\.kts)?\z},
  %r{\Asettings\.gradle(?:\.kts)?\z},
  %r{\Agradle\.properties\z},
  %r{\Agradle/},
  %r{\Agradlew(?:\.bat)?\z}
].freeze

paths = $stdin.each_line.map(&:strip).reject(&:empty?)
scope = if paths.empty?
          "full"
        elsif paths.any? { |path| FULL_TEST_PATHS.any? { |pattern| pattern.match?(path) } }
          "full"
        else
          "workflow-only"
        end

puts scope
