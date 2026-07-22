#!/usr/bin/env ruby

require "json"
require_relative "validate_backlog"

path = ARGV.shift
abort "usage: select_ready_tasks.rb <backlog.yml> [--merged JSON] [--active JSON]" unless path
merged = []
active = []
until ARGV.empty?
  flag = ARGV.shift
  value = ARGV.shift
  case flag
  when "--merged" then merged = JSON.parse(value)
  when "--active" then active = JSON.parse(value)
  else abort "unknown option: #{flag}"
  end
end

begin
  data = Backlog.load_and_validate(path)
  puts JSON.pretty_generate(Backlog.ready(data, merged: merged, active: active))
rescue StandardError => error
  warn "INVALID: #{error.message}"
  exit 1
end
