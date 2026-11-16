# AutoWatcher

Локальный мониторинг автомобильных объявлений: Java 21, Spring Boot 3.5, Maven, SQLite и Flyway.

## Фактическое состояние

Добавлен первый вертикальный срез:
`SearchProfile → FakeListingSource → MatchingService → SQLite → NotificationOutbox → тестовая доставка`.

- REST CRUD профилей и единый формат ошибок Problem Detail.
- Источник `FAKE` доступен только с Spring-профилем `demo`, возвращает четыре стабильных объявления с пагинацией.
- UPSERT по `(source, external_id)`, сохранение MATCH / NO_MATCH / UNKNOWN.
- Только MATCH создаёт outbox. Повторы проверки и изменения цены не создают повторных событий.
- Запись страницы, результатов сопоставления, первого совпадения и outbox атомарна.
- История проверок, диагностика ошибок источников и восстановление незавершённых запусков.
- Тестовая доставка записывает локальную квитанцию; Telegram пока не отправляется.
- Очистка деталей сохраняет идентификаторы и журнал совпадений, чтобы не терять дедупликацию.
- Новая Flyway V2; V1 не изменена.
- Maven Wrapper 3.3.2 / Maven 3.9.9, CI для Java 21 на Windows и Linux.

**Этот срез не является полным пользовательским MVP.** Avito и Auto.ru остаются заглушками.
Telegram, веб-панель, расписание, настройки частоты, Windows-пакет с Java Runtime ещё не реализованы.
Полный статус и чеклист: [docs/MVP_STATUS.md](docs/MVP_STATUS.md).

## Сборка и запуск

Для разработки нужен JDK 21 с настроенным `JAVA_HOME`.
Maven отдельно устанавливать не требуется; первый запуск Wrapper скачивает Maven и зависимости.

Windows PowerShell:

```powershell
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Linux/macOS:

```sh
./mvnw -B -ntp clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Или после сборки:
`java -jar target/autowatcher-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo`.

Приложение слушает `127.0.0.1:8080`. Проверка: `GET /actuator/health`.
Данные сохраняются в `./data/autowatcher.db`. Переменные: `AUTOWATCHER_PORT`, `AUTOWATCHER_DB`.
Для другого пути БД заранее создай его родительскую папку.
Не запускай несколько экземпляров на одной БД.

## Проверка полного цикла в PowerShell

```powershell
$base = "http://127.0.0.1:8080/api/v1/profiles"
$body = @{
  name = "BMW demo"
  criteria = @{ brand = "BMW"; priceTo = 3000000 }
  sources = @("FAKE")
} | ConvertTo-Json -Depth 4
$profile = Invoke-RestMethod -Method Post -Uri $base -ContentType "application/json" -Body $body
$id = $profile.id
Invoke-RestMethod -Method Post -Uri "$base/$id/checks"
Invoke-RestMethod -Uri "$base/$id/results"
Invoke-RestMethod -Uri "$base/$id/outbox"
Invoke-RestMethod -Method Post -Uri "$base/$id/outbox/dispatch-test"
Invoke-RestMethod -Method Post -Uri "$base/$id/checks"
Invoke-RestMethod -Uri "$base/$id/outbox"
```

Ожидается: четыре результата, одно подходящее объявление, один UNKNOWN и два NO_MATCH.
Outbox содержит одно событие. После тестовой доставки оно имеет статус DELIVERED.
Повторная проверка не добавляет событие.
Фиктивные ссылки используют `example.invalid` и не ведут на настоящие объявления.

## API

| Метод | Путь | Назначение |
|---|---|---|
| GET / POST | `/api/v1/profiles` | Список / создание профиля |
| GET / PUT / DELETE | `/api/v1/profiles/{id}` | Просмотр / редактирование / удаление |
| PATCH | `/api/v1/profiles/{id}/status` | `{"enabled":true}` |
| POST | `/api/v1/profiles/{id}/checks` | Синхронная проверка всех выбранных источников |
| GET | `/api/v1/profiles/{id}/runs` | Последние 100 запусков |
| GET | `/api/v1/profiles/{id}/results` | Последние 200 результатов |
| GET | `/api/v1/profiles/{id}/outbox` | Последние 200 событий и тестовые квитанции |
| POST | `/api/v1/profiles/{id}/outbox/dispatch-test` | Тестовая доставка до 100 событий |

Ошибка источника возвращается в запуске со статусом FAILED; это не успешная пустая выдача.
Успешные страницы уже зафиксированы, даже если следующая страница завершилась ошибкой.
Повторная проверка безопасна благодаря идентичности объявлений и журналу совпадений.
Политика первого среза: при первом совпадении тоже ставить уведомление; новая цена повторно не уведомляет.

## Проверки и ограничения

Добавлены интеграционные тесты на настоящем SQLite и Flyway, атомарность outbox,
повторную проверку, UNKNOWN → MATCH, очистку, отключённый профиль, ошибки источника и ошибки API.
CI также запускает собранный JAR и проверяет полный demo-цикл через HTTP.

Проверено в GitHub Actions на Windows и Linux: `clean verify`, 15 тестов без ошибок,
запуск собранного JAR, health и полный demo-цикл через HTTP.
[Успешный запуск CI](https://github.com/DeksFleks/AutoWatcher/actions/runs/43976099226)
для коммита `1a36915d6c63b4e90fd4050838726fad73856b5b`.
Локальная среда подготовки была недоступна; проверки выполнены на GitHub runners.

Тестовый dispatcher намеренно не выполняет внешнюю доставку: квитанция и подтверждение
фиксируются одной транзакцией. Для Telegram потребуется отдельный транспорт с retry/backoff
и обработкой возможной повторной внешней отправки.
Очистка в этом срезе доступна как `ListingRetentionService.compactBefore(Instant)` и не запускается автоматически.
Идентификаторы и журналы дедупликации сохраняются бессрочно; это не жёсткое ограничение размера БД.

Не коммить токены, локальные БД и персональные данные.
