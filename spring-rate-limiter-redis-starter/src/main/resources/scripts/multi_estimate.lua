local keyCount = #KEYS
local nowMillis = tonumber(ARGV[1])
local requested = tonumber(ARGV[2])

local planned = {}
local hasDeniedLimiter = false

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
    tokens = math.min(capacity, tokens + (intervals * refillRate))
    lastRefill = lastRefill + (intervals * refillPeriodMillis)
  else
    tokens = math.min(capacity, tokens)
  end

  local allowed = tokens >= requested
  local nextTime

  if allowed then
    nextTime = lastRefill + refillPeriodMillis
  else
    local missingTokens = requested - tokens
    local periodsNeeded = math.ceil(missingTokens / refillRate)
    nextTime = lastRefill + (periodsNeeded * refillPeriodMillis)
  end

  planned[i] = {
    tokens = tokens,
    lastRefill = lastRefill,
    expireInMillis = expireInMillis,
    hadKey = value ~= false and value ~= nil,
    allowed = allowed,
    nextTime = nextTime
  }

  if not allowed then
    hasDeniedLimiter = true
  end
end

if hasDeniedLimiter then
  local latestFailure = nil

  for i = 1, keyCount do
    local item = planned[i]
    if item.hadKey then
      redis.call('PEXPIRE', KEYS[i], item.expireInMillis)
    else
      redis.call('SET', KEYS[i], item.tokens .. ':' .. item.lastRefill, 'PX', item.expireInMillis)
    end

    if not item.allowed then
      if not latestFailure or item.nextTime > latestFailure.nextTime then
        latestFailure = item
      end
    end
  end

  return {0, latestFailure.tokens, latestFailure.nextTime}
end

local minRemaining = nil
local selectedNextTime = nil

for i = 1, keyCount do
  local item = planned[i]
  local state = item.tokens .. ':' .. item.lastRefill
  redis.call('SET', KEYS[i], state, 'PX', item.expireInMillis)

  if minRemaining == nil or item.tokens < minRemaining then
    minRemaining = item.tokens
    selectedNextTime = item.nextTime
  end
end

return {1, minRemaining, selectedNextTime}
