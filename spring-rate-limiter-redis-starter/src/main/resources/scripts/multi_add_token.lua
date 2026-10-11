local keyCount = #KEYS
local nowMillis = tonumber(ARGV[1])
local requested = tonumber(ARGV[2])

for i = 1, keyCount do
  local p = ((i - 1) * 5) + 2
  local initial = tonumber(ARGV[p + 1])
  local capacity = tonumber(ARGV[p + 2])
  local refillRate = tonumber(ARGV[p + 3])
  local refillPeriodMillis = tonumber(ARGV[p + 4])
  local expireInMillis = tonumber(ARGV[p + 5])

  local value = redis.call('GET', KEYS[i])
  local tokens, lastRefill

  if not value then
    tokens = initial
    lastRefill = nowMillis
  else
    local t_str, l_str = string.match(value, '^([^:]+):([^:]+)$')
    tokens = tonumber(t_str)
    lastRefill = tonumber(l_str)
  end

  local intervals = math.floor((nowMillis - lastRefill) / refillPeriodMillis)
  if intervals > 0 then
    tokens = math.min(capacity, tokens + (intervals * refillRate) + requested)
    lastRefill = lastRefill + (intervals * refillPeriodMillis)
  else
    tokens = math.min(capacity, tokens + requested)
  end

  redis.call('SET', KEYS[i], tokens .. ':' .. lastRefill, 'PX', expireInMillis)
end

return 1
