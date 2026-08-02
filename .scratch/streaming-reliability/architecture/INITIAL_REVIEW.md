# Initial architecture review — streaming reliability

## Existing seams

- `StreamingPlaybackSessionFactory` — внешний seam конфигурации только потокового Media3;
  локальный `PlayerScreen` имеет собственный builder.
- `EpisodeStreamResolver` — уже существующий seam повторного resolve и смены провайдера.
- `VetroVideo` — rendition/source identity, достаточная для выбора fallback без миграции модели.
- `StreamingPlaybackDiagnostics` — начатый adapter для `AnalyticsListener`, пока неглубокий.

## Findings

### REQUIRED_BEFORE_IMPLEMENTATION

- Прежний `createPlayer()` создавал одну `DefaultMediaSourceFactory`, а Activity затем подставляла
  `MediaSource`, созданный второй фабрикой. Transfer monitor необходимо создавать вместе с player
  и source за одним небольшим session seam, иначе он не увидит реальные запросы.

### REQUIRED_DURING_IMPLEMENTATION

- Разделить два разных механизма: adaptive cancellation внутри одного manifest и recovery между
  независимыми `VetroVideo`. Они не должны притворяться одной абстракцией.
- Вынести policy из Compose Activity в чистый глубокий module: Activity сообщает факты и выполняет
  одно решение, а пороги/счётчики/cooldown остаются локализованы и тестируемы.
- Transfer callbacks должны только агрегировать ограниченную статистику; форматирование и Logcat
  остаются в analytics adapter.

### FOLLOW_UP

- Перенести orchestration streaming player из большой Compose-функции в lifecycle-aware holder или
  ViewModel после стабилизации поведения. Сейчас это увеличит конфликт с незакоммиченными правками.
- Версионный upgrade Media3 — отдельный пакет с DASH regression fixture.

### NOT_RELEVANT_TO_SCOPE

- Host-health persistence и ML/MPC не оправданы до появления полевых метрик.

## Stable modules

- `localplayer` и его buffer defaults.
- Resolver implementations провайдеров.
- UI controls/gestures and download pipeline.

## Interface decision

Внешний interface фабрики должен вернуть одну streaming session, за которой скрыты общий
DataSource factory, transfer tracker, allocator, diagnostics и ExoPlayer. Activity получает player и
узкий health snapshot; детали сетевой телеметрии наружу не выходят.

## Risks to review

- Гонки старого player listener после пересоздания.
- Параллельные manifest/audio/video transfer callbacks.
- Ложная отмена при неизвестном размере или коротком burst.
- Recovery loop при повторном resolve того же URL.
- Утечка signed URL/header/error message в лог.
