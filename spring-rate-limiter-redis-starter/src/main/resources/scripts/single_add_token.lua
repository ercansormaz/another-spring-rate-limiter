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
  local tokensToAdd = math.min(capacity, initial + requested)
  redis.call('SET', key, tokensToAdd .. ':' .. nowMillis, 'PX', expireInMillis)
  return 1
end

local t_str, l_str = string.match(value, "^([^:]+):([^:]+)$")
local tokens = tonumber(t_str)
local lastRefill = tonumber(l_str)

local intervals = math.floor((nowMillis - lastRefill) / refillPeriodMillis)

if intervals > 0 then
  tokens = math.min(capacity, tokens + (intervals * refillRate) + requested)
  lastRefill = lastRefill + (intervals * refillPeriodMillis)
else
  tokens = math.min(capacity, tokens + requested)
end

redis.call('SET', key, tokens .. ':' .. lastRefill, 'PX', expireInMillis)
return 1