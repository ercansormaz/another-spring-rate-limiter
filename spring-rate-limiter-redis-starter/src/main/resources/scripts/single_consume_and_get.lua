local key = KEYS[1]
local nowMillis = tonumber(ARGV[1])
local requested = tonumber(ARGV[2])
local initial = tonumber(ARGV[3])
local capacity = tonumber(ARGV[4])
local refillRate = tonumber(ARGV[5])
local refillPeriodMillis = tonumber(ARGV[6])
local expireInMillis = tonumber(ARGV[7])

local value = redis.call('GET', key)

if not value then
  if initial >= requested then
    local remaining = initial - requested
    redis.call('SET', key, remaining .. ':' .. nowMillis, 'PX', expireInMillis)
    return { 1, remaining, nowMillis + refillPeriodMillis }
  else
    redis.call('SET', key, initial .. ':' .. nowMillis, 'PX', expireInMillis)

    local missingTokens = requested - initial
    local periodsNeeded = math.ceil(missingTokens / refillRate)
    local nextSufficientRefillTime = nowMillis + (periodsNeeded * refillPeriodMillis)

    return { 0, initial, nextSufficientRefillTime }
  end
end

local t_str, l_str = string.match(value, "^([^:]+):([^:]+)$")
local tokens = tonumber(t_str)
local lastRefill = tonumber(l_str)

local intervals = math.floor((nowMillis - lastRefill) / refillPeriodMillis)

if intervals > 0 then
  tokens = math.min(capacity, tokens + (intervals * refillRate))
  lastRefill = lastRefill + (intervals * refillPeriodMillis)
else
  tokens = math.min(capacity, tokens)
end

if tokens >= requested then
  local remaining = tokens - requested
  redis.call('SET', key, remaining .. ':' .. lastRefill, 'PX', expireInMillis)
  return { 1, remaining, lastRefill + refillPeriodMillis }
else
  redis.call('PEXPIRE', key, expireInMillis)

  local missingTokens = requested - tokens
  local periodsNeeded = math.ceil(missingTokens / refillRate)
  local nextSufficientRefillTime = lastRefill + (periodsNeeded * refillPeriodMillis)

  return { 0, tokens, nextSufficientRefillTime }
end