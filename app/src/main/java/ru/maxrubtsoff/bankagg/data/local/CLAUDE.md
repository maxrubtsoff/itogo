# Правила хранения (Room)

Загружается, когда работаешь в `data/local/**`. Общая карта — в корневом `CLAUDE.md`.

Здесь Room: `*Entity`, `*Dao`, `AppDatabase`, `*Mappers` (Entity ↔ domain). Слой `data` реализует интерфейсы домена; наружу (в `domain`/`ui`) отдаются доменные модели, не Entity.

## Схема и миграции
- Меняешь схему — **поднимаешь `version`** в `@Database` и пишешь `Migration` (данные пользователя не стираем). Текущая версия и цепочка миграций — в `AppDatabase.kt`.
- `fallbackToDestructiveMigration()` остаётся только для старых схем (до v10). С v10 каждая смена схемы идёт с миграцией.
- Новую entity добавляешь в массив `entities` и `abstract fun dao()` в `AppDatabase`.

## Инструменты
- **KSP**, не kapt (`ksp("androidx.room:room-compiler")`).
- БД шифруется: SQLCipher (`net.zetetic:sqlcipher-android`), ключ готовит `DatabaseCipher` (хранится в Android Keystore, см. `data/security`). Открытие БД идёт через `SupportOpenHelperFactory` — не ломай этот путь при правках `AppDatabase.get`.

## Мапперы
Entity ↔ domain — через `*Mappers`. Доменные модели неизменяемы (стабильность для Compose, `app/compose-stability.conf`); не тащи Room-аннотации в домен.

## Порядок добавления (слои)
Room идёт после домена и интерфейса репозитория, перед реализацией репозитория и DI — см. корневой `CLAUDE.md`, раздел «Как добавить фичу».
