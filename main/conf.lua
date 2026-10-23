function love.conf(t)
t.title = "قهرمان سایه ای"
t.console = true
t.name = "قهرمان سایه ای"
t.externalstorage = true
-- قرارداد ذخیره‌سازی با لایه Java: ریشه ذخیره LÖVE باید <ext>/save/archive باشد
-- تا chooser.txt، فایل‌های قفل اپیزود و *_save.txt با SavePaths.java هم‌مسیر شوند
-- (بدون این خط، LÖVE در اندروید identity را از نام آرشیو مشتق می‌کند و مسیرها ناسازگار می‌شوند)
t.identity = "archive"
t.window.fullscreen = love._os == "Android" or love._os == "iOS"
end