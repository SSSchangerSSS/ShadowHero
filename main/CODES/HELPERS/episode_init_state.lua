function episode_init()

local self = {}

local jupiter = require "jupiter"

-- شناسه‌های اپیزودهایی که واقعاً در CODES/LEVELS/level_chooser.lua پیاده‌سازی شده‌اند
local VALID_EPISODES = {
    train = true,
    s1e1 = true, s1e2 = true, s1e3 = true,
    s2e1 = true, s2e2 = true, s2e3 = true,
}

-- قرارداد مشترک با Java (gotoLevel/SavePaths): <save root>/chooser.txt
-- ریشه ذخیره LÖVE با t.identity="archive" در conf.lua روی <ext>/save/archive پین شده است
-- محتوا: "1=<episode_id>" — jupiter.load آن را به جدول {1="<episode_id>"} تبدیل می‌کند
local choosed_episode = jupiter.load("chooser.txt")

local episode = nil
if type(choosed_episode) == "table" and type(choosed_episode[1]) == "string" then
    episode = choosed_episode[1]
end
-- انتخاب نامعتبر/ناموجود/خراب هرگز به level ناقص یا اجرای خاموش منجر نمی‌شود؛
-- بازیابی کنترل‌شده: مرحله آموزشی همیشه موجود است.
if not VALID_EPISODES[episode] then
    episode = "train"
end

self.episode = episode --real
self.pause = false

self.set_pause = function(bool)
self.pause = bool
end

self.get_pause = function()
return self.pause
end

self.get_current_episode = function()
return self.episode
end

self.write_to_next = function(file_name,value_write)
if not love.filesystem.exists(file_name) then
data = {_fileName = file_name, value_write}
success = jupiter.save(data)
else
newData = jupiter.load(file_name)
if type(newData) == "table" then
newData[1] = value_write
newData._fileName = file_name
success1 = jupiter.save(newData)
end
end
end

self.unlock_next_episode = function()

if self.episode == "s1e1" then
self.write_to_next("season_one_e1.txt","1")
elseif self.episode == "s1e2" then
self.write_to_next("season_one_e2.txt","1")
elseif self.episode == "s1e3" then
self.write_to_next("season_one_e3.txt","1")
elseif self.episode == "s2e1" then
self.write_to_next("season_two_e1.txt","1")
elseif self.episode == "s2e2" then
self.write_to_next("season_two_e2.txt","1")
elseif self.episode == "s2e3" then
-- فصل ۳ هنوز ساخته نشده است؛ مثل نسخه اصلی، قفل فصل ۳ باز نمی‌شود
--self.write_to_next("season_two_e3.txt","1")
--elseif self.episode == "s3e1" then
--self.write_to_next("season_three_e1.txt","1")
--elseif self.episode == "s3e2" then
--self.write_to_next("season_three_e2.txt","1")
--[[other stage will come soon!
elseif self.episode == "s3e3" then
self.write_to_next("season_three_e3.txt","1")
elseif self.episode == "s4e1" then
self.write_to_next("season_four_e1.txt","1")
elseif self.episode == "s4e2" then
self.write_to_next("season_four_e2.txt","1")
--]]
end

end







return self

end
